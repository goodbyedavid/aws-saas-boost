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

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class OnboardingTest {

    @Test
    public void testOnboardingRequest() {
        //String json = "{\"name\": \"Santa Claus\"}";
        String json = "{\"name\": \"Santa Claus\", \"billingPlan\": \"free\"}";
        //String json = "{\"name\": \"Santa Claus\", \"tier\": \"default\", \"billingPlan\": \"foobar\"}";
        OnboardingRequest r = Utils.fromJson(json, OnboardingRequest.class);
        //System.out.println(Utils.toJson(r));
    }


    @Test
    public void testStacksComplete() {
        OnboardingStack stack1 = new OnboardingStack();
        OnboardingStack stack2 = new OnboardingStack();

        Onboarding onboarding = new Onboarding();
        assertFalse("No stacks", onboarding.stacksComplete());

        onboarding.setStacks(Arrays.asList(stack1, stack2));
        assertFalse("Empty stacks", onboarding.stacksComplete());

        stack1.setStatus("CREATE_COMPLETE");
        assertFalse("Not every stack is complete", onboarding.stacksComplete());

        stack2.setStatus("UPDATE_COMPLETE");
        assertTrue("All stacks complete", onboarding.stacksComplete());

        onboarding.addStack(new OnboardingStack());
        assertFalse("Not every stack is complete", onboarding.stacksComplete());
    }
}