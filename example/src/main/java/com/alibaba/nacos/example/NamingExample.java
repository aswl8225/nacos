/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.example;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.AbstractEventListener;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.NamingEvent;

import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Nacos naming example.
 * <p>Add the JVM parameter to run the NamingExample:</p>
 * {@code -DserverAddr=${nacos.server.ip}:${nacos.server.port} -Dnamespace=${namespaceId}}
 *
 * @author nkorange
 */
public class NamingExample {

    public static void main(String[] args) throws NacosException, InterruptedException {

        Properties properties = new Properties();
        properties.setProperty("serverAddr", "localhost:8848");
        properties.setProperty("namespace", "public");

        NamingService naming = NamingFactory.createNamingService(properties);

        naming.registerInstance("nacos.test.1", "192.168.43.37", 8888, "TEST1");
//
//        naming.registerInstance("nacos.test.2", "192.168.43.37", 9999, "DEFAULT");
//
//        System.out.println(naming.getAllInstances("nacos.test.3"));

//        naming.deregisterInstance("nacos.test.3", "2.2.2.2", 9999, "DEFAULT");

//        System.out.println(naming.getAllInstances("nacos.test.3"));
//
//        Executor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
//                new ThreadFactory() {
//                    @Override
//                    public Thread newThread(Runnable r) {
//                        Thread thread = new Thread(r);
//                        thread.setName("test-thread");
//                        return thread;
//                    }
//                });
//
//        naming.subscribe("nacos.test.3", new AbstractEventListener() {
//
//            //EventListener onEvent is sync to handle, If process too low in onEvent, maybe block other onEvent callback.
//            //So you can override getExecutor() to async handle event.
//            @Override
//            public Executor getExecutor() {
//                return executor;
//            }
//
//            @Override
//            public void onEvent(Event event) {
//                System.out.println(((NamingEvent) event).getServiceName());
//                System.out.println(((NamingEvent) event).getInstances());
//            }
//        });


        Thread.sleep(Integer.MAX_VALUE);
    }
}
