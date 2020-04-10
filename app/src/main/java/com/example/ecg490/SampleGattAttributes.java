/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.ecg490;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {

    //public static final UUID RX_SERVICE_UUID = UUID.fromString("59462F12-9543-9999-12C8-58B459A2712D");
    //public static final UUID RX_CHAR_UUID = UUID.fromString("5C3A659E-897E-45E1-B016-007107C96DF6");


    private static HashMap<String, String> attributes = new HashMap();
    public static String RX_CHAR_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
    public static String CCCD = "00002902-0000-1000-8000-00805f9b34fb";

    static {
        // Sample Services.
        attributes.put("6e400001-b5a3-f393-e0a9-e50e24dcca9e", "W2ECG");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Sample Characteristics.
        attributes.put(RX_CHAR_UUID, "W2ECG Data");
        //attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
