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

package com.alibaba.nacos.naming.healthcheck;

import com.alibaba.nacos.common.http.Callback;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.utils.IPUtil;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import com.alibaba.nacos.naming.healthcheck.heartbeat.BeatCheckTask;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.HttpClient;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.UdpPushService;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Client beat check task of service for version 1.x.
 *
 * @author nkorange
 */
public class ClientBeatCheckTask implements BeatCheckTask {

    private Service service;

    public ClientBeatCheckTask(Service service) {
        this.service = service;
    }

    @JsonIgnore
    public UdpPushService getPushService() {
        return ApplicationUtils.getBean(UdpPushService.class);
    }

    @JsonIgnore
    public DistroMapper getDistroMapper() {
        return ApplicationUtils.getBean(DistroMapper.class);
    }

    public GlobalConfig getGlobalConfig() {
        return ApplicationUtils.getBean(GlobalConfig.class);
    }

    public SwitchDomain getSwitchDomain() {
        return ApplicationUtils.getBean(SwitchDomain.class);
    }

    @Override
    public String taskKey() {
        return KeyBuilder.buildServiceMetaKey(service.getNamespaceId(), service.getName());
    }

    @Override
    public void run() {
        try {
            // If upgrade to 2.0.X stop health check with v1
            if (ApplicationUtils.getBean(UpgradeJudgement.class).isUseGrpcFeatures()) {
                return;
            }
            /**
             * 是否由本地节点执行
             */
            if (!getDistroMapper().responsible(service.getName())) {
                return;
            }

            if (!getSwitchDomain().isHealthCheckEnabled()) {
                return;
            }
            /**
             * 获取所有的临时节点
             */
            List<Instance> instances = service.allIPs(true);

            // first set health status of instances:
            for (Instance instance : instances) {
                /**
                 * 心跳超时
                 */
                if (System.currentTimeMillis() - instance.getLastBeat() > instance.getInstanceHeartBeatTimeOut()) {
                    if (!instance.isMarked()) {
                        if (instance.isHealthy()) {
                            /**
                             * 设置为不健康的实例
                             */
                            instance.setHealthy(false);
                            Loggers.EVT_LOG
                                    .info("{POS} {IP-DISABLED} valid: {}:{}@{}@{}, region: {}, msg: client timeout after {}, last beat: {}",
                                            instance.getIp(), instance.getPort(), instance.getClusterName(),
                                            service.getName(), UtilsAndCommons.LOCALHOST_SITE,
                                            instance.getInstanceHeartBeatTimeOut(), instance.getLastBeat());
                            /**
                             * 发布ServiceChangeEvent  udp通知？
                             */
                            getPushService().serviceChanged(service);
//                            /**
//                             * 发布InstanceHeartbeatTimeoutEvent   留待二次开发
//                             */
//                            SpringContext.getAppContext().publishEvent(new InstanceHeartbeatTimeoutEvent(this, instance));
                        }
                    }
                }
            }

            if (!getGlobalConfig().isExpireInstance()) {
                return;
            }

            // then remove obsolete instances:
            /**
             * 删除废弃的instances
             */
            for (Instance instance : instances) {

                if (instance.isMarked()) {
                    continue;
                }
                /**
                 * 超过待删除时间  则删除
                 */
                if (System.currentTimeMillis() - instance.getLastBeat() > instance.getIpDeleteTimeout()) {
                    // delete instance
                    Loggers.SRV_LOG.info("[AUTO-DELETE-IP] service: {}, ip: {}", service.getName(),
                            JacksonUtils.toJson(instance));
                    /**
                     * 删除实例
                     */
                    deleteIp(instance);
                }
            }

        } catch (Exception e) {
            Loggers.SRV_LOG.warn("Exception while processing client beat time out.", e);
        }

    }

    private void deleteIp(Instance instance) {

        try {
            NamingProxy.Request request = NamingProxy.Request.newRequest();
            request.appendParam("ip", instance.getIp()).appendParam("port", String.valueOf(instance.getPort()))
                    .appendParam("ephemeral", "true").appendParam("clusterName", instance.getClusterName())
                    .appendParam("serviceName", service.getName()).appendParam("namespaceId", service.getNamespaceId());

            /**
             * 向本机发起删除交易
             */
            String url = "http://" + IPUtil.localHostIP() + IPUtil.IP_PORT_SPLITER + EnvUtil.getPort() + EnvUtil
                    .getContextPath() + UtilsAndCommons.NACOS_NAMING_CONTEXT + "/instance?" + request.toUrl();

            // delete instance asynchronously:
            /**
             * 删除操作
             */
            HttpClient.asyncHttpDelete(url, null, null, new Callback<String>() {
                @Override
                public void onReceive(RestResult<String> result) {
                    if (!result.ok()) {
                        Loggers.SRV_LOG
                                .error("[IP-DEAD] failed to delete ip automatically, ip: {}, caused {}, resp code: {}",
                                        instance.toJson(), result.getMessage(), result.getCode());
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    Loggers.SRV_LOG
                            .error("[IP-DEAD] failed to delete ip automatically, ip: {}, error: {}", instance.toJson(),
                                    throwable);
                }

                @Override
                public void onCancel() {

                }
            });

        } catch (Exception e) {
            Loggers.SRV_LOG
                    .error("[IP-DEAD] failed to delete ip automatically, ip: {}, error: {}", instance.toJson(), e);
        }
    }
}
