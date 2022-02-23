/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.partners.saasfactory.saasboost;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.*;
import software.amazon.awssdk.services.cloudformation.model.Stack;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.util.*;
import java.util.regex.Pattern;

public class OnboardingAppStackListener implements RequestHandler<SNSEvent, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnboardingAppStackListener.class);
    private static final String AWS_REGION = System.getenv("AWS_REGION");
    private static final String SAAS_BOOST_ENV = System.getenv("SAAS_BOOST_ENV");
    private static final String SAAS_BOOST_EVENT_BUS = System.getenv("SAAS_BOOST_EVENT_BUS");
    private static final String UPDATE_TENANT_RESOURCES = "TENANT_RESOURCES_CHANGE";
    private static final String EVENT_SOURCE = "saas-boost";
    private static final Pattern STACK_NAME_PATTERN = Pattern
            .compile("^sb-" + SAAS_BOOST_ENV + "-tenant-[a-z0-9]{8}-app-.+-.+$");
    private static final Collection<String> EVENTS_OF_INTEREST = Collections.unmodifiableCollection(
            Arrays.asList("CREATE_COMPLETE", "UPDATE_COMPLETE"));
    private final CloudFormationClient cfn;
    private final EventBridgeClient eventBridge;

    public OnboardingAppStackListener() {
        final long startTimeMillis = System.currentTimeMillis();
        if (Utils.isBlank(AWS_REGION)) {
            throw new IllegalStateException("Missing required environment variable AWS_REGION");
        }
        if (Utils.isBlank(SAAS_BOOST_EVENT_BUS)) {
            throw new IllegalStateException("Missing required environment variable SAAS_BOOST_EVENT_BUS");
        }
        this.cfn = Utils.sdkClient(CloudFormationClient.builder(), CloudFormationClient.SERVICE_NAME);
        this.eventBridge = Utils.sdkClient(EventBridgeClient.builder(), EventBridgeClient.SERVICE_NAME);
        LOGGER.info("Constructor init: {}", System.currentTimeMillis() - startTimeMillis);
    }

    @Override
    public Object handleRequest(SNSEvent event, Context context) {
        //LOGGER.info(Utils.toJson(event));

        List<SNSEvent.SNSRecord> records = event.getRecords();
        SNSEvent.SNS sns = records.get(0).getSNS();
        String message = sns.getMessage();

        CloudFormationEvent cloudFormationEvent = CloudFormationEventDeserializer.deserialize(message);

        // CloudFormation sends SNS notifications for every resource in a stack going through each status change.
        // We want to process the resources of the tenant-onboarding-app.yaml CloudFormation stack only after the
        // stack has finished being created or updated so we don't trigger anything downstream prematurely.
        if (filter(cloudFormationEvent)) {
            String stackName = cloudFormationEvent.getStackName();
            String stackStatus = cloudFormationEvent.getResourceStatus();
            LOGGER.info("Stack " + stackName + " is in status " + stackStatus);

            // We need to get the tenant and the application service this stack was run for
            String tenantId = null;
            String serviceName = null;
            try {
                DescribeStacksResponse stacks = cfn.describeStacks(req -> req
                        .stackName(cloudFormationEvent.getStackId())
                );
                Stack stack = stacks.stacks().get(0);
                for (Parameter parameter : stack.parameters()) {
                    if ("TenantId".equals(parameter.parameterKey())) {
                        tenantId = parameter.parameterValue();
                    }
                    if ("ServiceName".equals(parameter.parameterKey())) {
                        serviceName = parameter.parameterValue();
                    }
                }
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cfn:DescribeStacks error", cfnError);
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }

            // We use these to build the ARN of the resources we're interested in if we don't
            // get the ARN straight from the CloudFormation physical resource id
            final String[] lambdaArn = context.getInvokedFunctionArn().split(":");
            final String partition = lambdaArn[1];
            final String accountId = lambdaArn[4];

            // We're looking for CodePipeline repository resources in a CREATE_COMPLETE state. There could be
            // multiple pipelines provisioned depending on how the application services are configured.
            try {
                ListStackResourcesResponse resources = cfn.listStackResources(req -> req
                        .stackName(cloudFormationEvent.getStackId())
                );
                for (StackResourceSummary resource : resources.stackResourceSummaries()) {
//                    LOGGER.debug("Processing resource {} {} {} {}", resource.resourceType(),
//                            resource.resourceStatusAsString(), resource.logicalResourceId(),
//                            resource.physicalResourceId());
                    if ("CREATE_COMPLETE".equals(resource.resourceStatusAsString())) {
                        if ("AWS::CodePipeline::Pipeline".equals(resource.resourceType())) {
                            String codePipeline = resource.physicalResourceId();
                            // The resources collection on the tenant object is Map<String, Resource>
                            // so we need a unique key per service code pipeline. We'll prefix the
                            // key with SERVICE_ and suffix it with _CODE_PIPELINE so we can find
                            // all of the tenant's code pipelines later on by looking for that pattern.
                            String key = serviceNameResourceKey(serviceName, AwsResource.CODE_PIPELINE.name());
                            LOGGER.info("Publishing update tenant resources event for tenant {} {} {}", tenantId,
                                    key, codePipeline);

                            Map<String, Object> tenantResource = new HashMap<>();
                            tenantResource.put(key, Map.of(
                                    "name", codePipeline,
                                    "arn", AwsResource.CODE_PIPELINE.formatArn(partition, AWS_REGION, accountId,
                                            codePipeline),
                                    "consoleUrl", AwsResource.CODE_PIPELINE.formatUrl(AWS_REGION, codePipeline)
                            ));

                            // The update tenant resources API call is additive, so we don't need to pull the
                            // current tenant object ourselves.
                            Utils.publishEvent(eventBridge, SAAS_BOOST_EVENT_BUS, EVENT_SOURCE,
                                    UPDATE_TENANT_RESOURCES,
                                    Map.of("tenantId", tenantId, "resources", Utils.toJson(tenantResource))
                            );
                        }
//                        } else if ("AWS::CloudFormation::Stack".equals(resourceType) && "rds".equals(logicalId)) {
//                            //this is the rds sub-stack so get the cluster and instance ids
//                            getRdsResources(physicalResourceId, tenantResources);

//                    // Invoke this tenant's deployment pipeline... probably should be done through a service API call
//                    LOGGER.info("Triggering tenant deployment pipeline");
//                    // First, build an event object that looks similar to the object generated by
//                    // an ECR image push (which is what the deploy method uses).
//                    Map<String, Object> lambdaEvent = new HashMap<>();
//                    lambdaEvent.put("source", "tenant-onboarding");
//                    lambdaEvent.put("region", AWS_REGION);
//                    lambdaEvent.put("account", context.getInvokedFunctionArn().split(":")[4]);
//                    Map<String, Object> detail = new HashMap<>();
//                    detail.put("repository-name", ecrRepo);
//                    detail.put("image-tag", "latest");
//                    detail.put("tenantId", tenantId);
//                    detail.put("pipeline", pipeline);
//                    lambdaEvent.put("detail", detail);
//                    SdkBytes payload = SdkBytes.fromString(Utils.toJson(lambdaEvent), StandardCharsets.UTF_8);
//                    // Now invoke the deployment lambda async... probably move this to the tenant service
//                    try {
//                        lambda.invoke(r -> r
//                                .functionName(TENANT_DEPLOY_LAMBDA)
//                                .invocationType(InvocationType.EVENT)
//                                .payload(payload)
//                        );
//                    } catch (SdkServiceException lambdaError) {
//                        LOGGER.error("lambda:Invoke");
//                        LOGGER.error(Utils.getFullStackTrace(lambdaError));
//                        throw lambdaError;
//                    }
                    }
                }
            } catch (SdkServiceException cfnError) {
                LOGGER.error("cfn:ListStackResources error", cfnError);
                LOGGER.error(Utils.getFullStackTrace(cfnError));
                throw cfnError;
            }

//                // Persist the tenant specific things as tenant settings
//                if (dbHost != null) {
//                    LOGGER.info("Saving tenant database host setting");
//                    Map<String, Object> systemApiRequest = new HashMap<>();
//                    systemApiRequest.put("resource", "settings/tenant/" + tenantId + "/DB_HOST");
//                    systemApiRequest.put("method", "PUT");
//                    systemApiRequest.put("body", "{\"name\":\"DB_HOST\", \"value\":\"" + dbHost + "\"}");
//                    publishEvent(systemApiRequest, SYSTEM_API_CALL);
//                }
//                if (albName != null) {
//                    LOGGER.info("Saving tenant ALB setting");
//                    Map<String, Object> systemApiRequest = new HashMap<>();
//                    systemApiRequest.put("resource", "settings/tenant/" + tenantId + "/ALB");
//                    systemApiRequest.put("method", "PUT");
//                    systemApiRequest.put("body", "{\"name\":\"ALB\", \"value\":\"" + albName + "\"}");
//                    publishEvent(systemApiRequest, SYSTEM_API_CALL);
//                }
        }
        return null;
    }

    protected static String serviceNameResourceKey(String serviceName, String resourceType) {
        if (Utils.isBlank(serviceName)) {
            throw new IllegalArgumentException("Service name must not be blank");
        }
        if (Utils.isBlank(resourceType)) {
            throw new IllegalArgumentException("Resource type must not be blank");
        }
        return "SERVICE_"
                + serviceName.toUpperCase().replaceAll("\\s+", "_")
                + "_"
                + resourceType;
    }

    protected static boolean filter(CloudFormationEvent cloudFormationEvent) {
        return ("AWS::CloudFormation::Stack".equals(cloudFormationEvent.getResourceType())
                && STACK_NAME_PATTERN.matcher(cloudFormationEvent.getStackName()).matches()
                && EVENTS_OF_INTEREST.contains(cloudFormationEvent.getResourceStatus()));
    }

//                LOGGER.info("Stack Outputs:");
//                for (Output output : stack.outputs()) {
//                    LOGGER.info("{} => {}", output.outputKey(), output.outputValue());
//                    if ("RdsEndpoint".equals(output.outputKey())) {
//                        if (Utils.isNotBlank(output.outputValue())) {
//                            dbHost = output.outputValue();
//                        }
//                    }
//                    if ("BillingPlan".equals(output.outputKey())) {
//                        if (Utils.isNotBlank(output.outputValue())) {
//                            billingPlan = output.outputValue();
//                        }
//                    }
//                }

//    private void getRdsResources(String stackId, Map<String, String> consoleResources) {
//        try {
//            ListStackResourcesResponse resources = cfn.listStackResources(request -> request.stackName(stackId));
//            for (StackResourceSummary resource : resources.stackResourceSummaries()) {
//                String resourceType = resource.resourceType();
//                String physicalResourceId = resource.physicalResourceId();
//                AwsResource url = null;
//                if (resourceType.equalsIgnoreCase(AwsResource.RDS_INSTANCE.getResourceType())) {
//                    url = AwsResource.RDS_INSTANCE;
//                } else if (resourceType.equalsIgnoreCase(AwsResource.RDS_CLUSTER.getResourceType())) {
//                    url = AwsResource.RDS_CLUSTER;
//                }
//                if (url != null) {
//                    consoleResources.put(url.name(), url.formatUrl(AWS_REGION, physicalResourceId));
//                }
//            }
//        } catch (SdkServiceException cfnError) {
//            LOGGER.error("cfn:ListStackResources error", cfnError);
//            LOGGER.error(Utils.getFullStackTrace(cfnError));
//            throw cfnError;
//        }
//    }
}