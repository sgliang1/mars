package com.mars.post.infrastructure.monitor;

import com.mars.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/monitor")
@CrossOrigin(origins = "*") // 解决跨域问题，允许大屏页面访�?
public class MonitorController {

    @Autowired
    private DiscoveryClient discoveryClient;

    /**
     * 获取 Nacos 中真实的节点状�?
     */
    @GetMapping("/nacos-nodes")
    public Result<List<Map<String, Object>>> getNacosNodes() {
        List<Map<String, Object>> serviceStatusList = new ArrayList<>();
        try {
            // 获取 Nacos 中注册的所有服务名
            List<String> services = discoveryClient.getServices();

            for (String serviceId : services) {
                // 获取每个服务名下的所有实例详�?
                List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);

                Map<String, Object> serviceInfo = new HashMap<>();
                serviceInfo.put("name", serviceId);
                serviceInfo.put("instanceCount", instances.size());
                // 只要有实例在线，状态即�?Online
                serviceInfo.put("status", !instances.isEmpty() ? "Online" : "Offline");

                // 提取具体�?IP:Port 列表
                List<String> nodes = new ArrayList<>();
                for (ServiceInstance instance : instances) {
                    nodes.add(instance.getHost() + ":" + instance.getPort());
                }
                serviceInfo.put("nodes", nodes);

                serviceStatusList.add(serviceInfo);
            }
        } catch (Exception e) {
            return Result.fail("火星基地扫描失败: " + e.getMessage());
        }
        return Result.success(serviceStatusList);
    }
}
