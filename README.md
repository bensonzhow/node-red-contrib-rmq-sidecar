# node-red-contrib-rmq-sidecar

自带 RocketMQ 4.x 边车（原生二进制），Node-RED 通过 REST 调用实现发送/消费，避免 Node.js 直连 4.x 的兼容问题。

如果使用RocketMQ 5.x 使用另一个插件 https://www.npmjs.com/package/node-red-zrocketmq

## 安装 & 构建

1) 安装 GraalVM JDK 17，`gu install native-image`，安装 Maven。
2) Windows:

   ```powershell
   cd node-red-contrib-rmq-sidecar\scripts
   .\build-native-windows.ps1
   npm run verify
   npm pack
   
3) Linux:

    ```sh
    cd node-red-contrib-rmq-sidecar/scripts
    ./build-native-linux.sh
    npm run verify
    npm pack
    ```


```sh
mvn -U -q -DskipTests clean package
mvn -q -DskipTests -Pnative native:compile



# 查看是否有cl.exe
win 搜索：x64 Native Tools Command Prompt for VS 2022
cl /Bv
# 用于 x64 的 Microsoft (R) C/C++ 优化编译器 19.40.33813 版
# 版权所有(C) Microsoft Corporation。保留所有权利。
# 
# 编译器扫描遍数:
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\cl.exe:        版本 19.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\c1.dll:        版本 19.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\c1xx.dll:      版本 19.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\c2.dll:        版本 19.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\c1xx.dll:      版本 19.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\link.exe:      版本 14.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\mspdb140.dll:  版本 14.40.33813.0
#  E:\Vs\VC\Tools\MSVC\14.40.33807\bin\HostX64\x64\2052\clui.dll: 版本 19.40.33813.0

powershell -ExecutionPolicy Bypass -File build-native-windows.ps1





⸻   启动

启动 sidecar

    ```
    1.在画布放入 rmq-sidecar 节点（默认端口 18080）。
    2.连接一个 inject 节点（任意触发即可启动 sidecar）。
    3.Debug 节点会看到 {"ok":true,"baseUrl":"http://127.0.0.1:18080"}。
    ```
sidecar 仅监听 127.0.0.1，更安全。端口可在节点属性里改。
    ```
    访问：http://127.0.0.1:18080/actuator/health
    {"status":"UP"}：就表示 sidecar 已正常启动，你可以继续用 rmq-producer / rmq-consumer 节点收发消息。
    ```


发送 Producer
    •放入 rmq-producer 节点（Base URL 填 http://127.0.0.1:18080）。
    •注入前面生成的消息参数：
    ```js
        // Function 节点示例
        msg.rocketmq = {
        namesrv: "MQ_INST_xxx:9876",   // 你的 NameServer（4.x）
        producerGroup: "PG_JD_PLAN",
        topic: "GK_JD_PLAN_WORKING_STATUS_TOPIC",
        tag: "status",
        // 如果服务端开启 ACL：
        // accessKey: "xxx",
        // accessSecret: "yyy",
        sendTimeoutMs: 10000
        };
        msg.payload = {
        startTime: '2020-10-10 10:10:10',
        plantNo: '007',
        curWkStatusCode: '02',
        previousState: '04',
        receiveTime: '2020-10-10 10:10:10'
        };
        return msg;
    ```
    产出 payload 类似
    ```json
    { "ok": true, "msgId": "...", "status": "SEND_OK" }
    ```
消费 Consumer
    •放入 rmq-consumer 节点（Base URL 填 http://127.0.0.1:18080）。
    •“启动”命令
    ```js
    // Function 节点 -> rmq-consumer
    msg.cmd = "start";
    msg.rocketmq = {
    namesrv: "MQ_INST_xxx:9876",
    consumerGroup: "CG_JD_PLAN",
    topic: "GK_JD_PLAN_WORKING_STATUS_TOPIC",
    tag: "*",             // 可省略，默认 *
    bufferSize: 1000,     // 内部队列（有界，避免内存涨）
    threadNums: 2,
    maxReconsumeTimes: 16,
    // accessKey: "...", accessSecret: "..."
    };
    return msg;
    ```
    消息将逐条从 rmq-consumer 输出，形如
    ```json
    {
    "msgId":"...",
    "topic":"GK_JD_PLAN_WORKING_STATUS_TOPIC",
    "tags":"status",
    "keys":null,
    "bornTimestamp":...,
    "storeTimestamp":...,
    "body":"{...}"          // 字符串
    }
    ```
停止
    ```js
    msg.cmd = "stop";
    msg.rocketmq = {
    namesrv: "MQ_INST_xxx:9876",
    consumerGroup: "CG_JD_PLAN",
    topic: "GK_JD_PLAN_WORKING_STATUS_TOPIC"
    };
    return msg;
    ```
