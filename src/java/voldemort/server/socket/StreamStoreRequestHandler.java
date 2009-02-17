/*
 * Copyright 2008-2009 LinkedIn, Inc
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

package voldemort.server.socket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import voldemort.VoldemortException;
import voldemort.serialization.VoldemortOpCode;
import voldemort.store.ErrorCodeMapper;
import voldemort.store.RebalancingStore;
import voldemort.store.Store;
import voldemort.utils.ByteUtils;
import voldemort.versioning.VectorClock;
import voldemort.versioning.Versioned;

/**
 * Responsible for interpreting and handling a single request stream
 * 
 * @author jay
 * 
 */
public class StreamStoreRequestHandler {

    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final ConcurrentMap<String, ? extends Store<byte[], byte[]>> storeMap;

    private ErrorCodeMapper errorMapper = new ErrorCodeMapper();

    public StreamStoreRequestHandler(ConcurrentMap<String, ? extends Store<byte[], byte[]>> storeMap,
                                     DataInputStream inputStream,
                                     DataOutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.storeMap = storeMap;
    }

    public void handleRequest() throws IOException {
        byte opCode = inputStream.readByte();
        String storeName = inputStream.readUTF();
        Store<byte[], byte[]> store = storeMap.get(storeName);
        byte[] key;
        if(store == null) {
            writeException(outputStream, new VoldemortException("No store named '" + storeName
                                                                + "'."));
        } else {
            switch(opCode) {
                case VoldemortOpCode.GET_OP_CODE:
                    key = readKey(inputStream);
                    handleGet(store, key);
                    break;
                case VoldemortOpCode.PUT_OP_CODE:
                    key = readKey(inputStream);
                    handlePut(store, key);
                    break;
                case VoldemortOpCode.DELETE_OP_CODE:
                    key = readKey(inputStream);
                    handleDelete(store, key);
                    break;
                case VoldemortOpCode.GET_PARTITION_AS_STREAM_OP_CODE:
                    handleGetPartitionsAsStream(store);
                    break;
                case VoldemortOpCode.PUT_PARTITION_AS_STREAM_OP_CODE:
                    handlePutPartitionsAsStream(store);
                    break;
                default:
                    throw new IOException("Unknown op code: " + opCode);
            }
        }
        outputStream.flush();
    }

    private byte[] readKey(DataInputStream inputStream) throws IOException {
        int keySize = inputStream.readInt();
        if(keySize == -1) {
            return null;
        }
        byte[] key = new byte[keySize];
        ByteUtils.read(inputStream, key);

        return key;
    }

    private void handleGet(Store<byte[], byte[]> store, byte[] key) throws IOException {
        List<Versioned<byte[]>> results = null;
        try {
            results = store.get(key);
            outputStream.writeShort(0);
        } catch(VoldemortException e) {
            e.printStackTrace();
            writeException(outputStream, e);
            return;
        }
        outputStream.writeInt(results.size());
        for(Versioned<byte[]> v: results) {
            byte[] clock = ((VectorClock) v.getVersion()).toBytes();
            byte[] value = v.getValue();
            outputStream.writeInt(clock.length + value.length);
            outputStream.write(clock);
            outputStream.write(value);
        }
    }

    private void handlePut(Store<byte[], byte[]> store, byte[] key) throws IOException {
        int valueSize = inputStream.readInt();
        byte[] bytes = new byte[valueSize];
        ByteUtils.read(inputStream, bytes);
        VectorClock clock = new VectorClock(bytes);
        byte[] value = ByteUtils.copy(bytes, clock.sizeInBytes(), bytes.length);
        try {
            store.put(key, new Versioned<byte[]>(value, clock));
            outputStream.writeShort(0);
        } catch(VoldemortException e) {
            writeException(outputStream, e);
        }
    }

    private void handleDelete(Store<byte[], byte[]> store, byte[] key) throws IOException {
        int versionSize = inputStream.readShort();
        byte[] versionBytes = new byte[versionSize];
        ByteUtils.read(inputStream, versionBytes);
        VectorClock version = new VectorClock(versionBytes);
        try {
            boolean succeeded = store.delete(key, version);
            outputStream.writeShort(0);
            outputStream.writeBoolean(succeeded);
        } catch(VoldemortException e) {
            writeException(outputStream, e);
        }
    }

    private void handlePutPartitionsAsStream(Store<byte[], byte[]> store) throws IOException {
        if(!implementsInterface(store, RebalancingStore.class)) {
            throw new VoldemortException("Store " + store.getName()
                                         + " doesnot support Partition Rebalancing");

        }
        RebalancingStore rebalancingStore = (RebalancingStore) store;
        try {
            rebalancingStore.putPartitionsAsStream(inputStream);
            outputStream.writeShort(0);
        } catch(VoldemortException e) {
            writeException(outputStream, e);
        }
    }

    private void handleGetPartitionsAsStream(Store<byte[], byte[]> store) throws IOException {
        // check for RebalancingStore and throw error if not.
        if(!implementsInterface(store, RebalancingStore.class)) {
            throw new VoldemortException("Store " + store.getName()
                                         + " doesnot support Partition Rebalancing");

        }
        RebalancingStore rebalancingStore = (RebalancingStore) store;

        // read partition List
        int partitionSize = inputStream.readInt();
        int[] partitionList = new int[partitionSize];
        for(int i = 0; i < partitionSize; i++) {
            partitionList[i] = inputStream.readInt();
        }

        try {
            DataInputStream storeStream = rebalancingStore.getPartitionsAsStream(partitionList);
            outputStream.writeShort(0);

            int keySize = storeStream.readInt();
            while(keySize != -1) {
                outputStream.writeInt(keySize); // write keySize

                byte[] key = new byte[keySize];
                ByteUtils.read(inputStream, key);
                outputStream.write(key); // write key

                int valueSize = inputStream.readInt();
                outputStream.writeInt(valueSize); // write valueSize

                byte[] value = new byte[valueSize];
                ByteUtils.read(inputStream, value);
                outputStream.write(value); // write value

                keySize = storeStream.readInt(); // read New KeySize
            }

        } catch(VoldemortException e) {
            writeException(outputStream, e);
        }
    }

    private boolean implementsInterface(Object obj, Class ifc) {
        for(Class c: obj.getClass().getInterfaces()) {
            if(c.getName().equals(ifc.getName())) {
                return true;
            }
        }
        return false;
    }

    private void writeException(DataOutputStream stream, VoldemortException e) throws IOException {
        short code = errorMapper.getCode(e);
        stream.writeShort(code);
        stream.writeUTF(e.getMessage());
    }
}
