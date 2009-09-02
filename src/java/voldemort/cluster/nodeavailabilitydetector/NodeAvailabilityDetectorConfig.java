/*
 * Copyright 2009 Mustard Grain, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.cluster.nodeavailabilitydetector;

import voldemort.cluster.Node;
import voldemort.store.Store;
import voldemort.utils.ByteArray;

public interface NodeAvailabilityDetectorConfig {

    public String getImplementationClassName();

    public long getNodeBannagePeriod();

    public Store<ByteArray, byte[]> getStore(Node node);

}
