# Goodix BLE DFU SDK(Android版)集成方法

[EN](README.md)   |  [中文](README_zh.md)



## 1. 简介
Android DFU SDK 由深圳市汇顶科技股份有限公司提供，主要用于对由汇顶科技研发的低功耗蓝牙芯片进行固件程序升级，此SDK已全面开放源码，用户可以快速的集成到自己的APP项目中。

## 2. 集成步骤

### 2.1 项目导入
导入SDK 源码中的libcom 和libdfu2 模块加载至工程，模块说明如下：
1. libcom ：模块中包含了常用的工具类。主要供libdfu2使用，集成时需导入。
2. libdfu2 ：模块中包含了DFU升级相关的类，支持2种DFU升级模式:
   1. DFU v2模式: 涉及到EasyDfu2 类, 是最新开发的模式，推荐用户使用
   2. FastDFU模式: 涉及 FastDFU 类，一种传统的升级模式, 集成时需导入, 新用户不推荐使用。
3. app ：项目Application主程序，实现了一个简洁的DFU升级应用程序，无需导入，但建议用户在集成时参考其实现，特别是其中的MainActivity 和 TestCase类。

### 2.2 调用DFU API

#### 2.2.1 DFU V2 模式下接口调用

1. 普通模式升级
```java
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startDfu(ctx, device, file);
```
2. 双区拷贝模式升级
```java
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startDfuInCopyMode(ctx, device, file, copyAddress); 
```

3. 资源文件升级
```java
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startUpdateResource(ctx, device, file, extFlash, writeAddress);

        return dfu2;
```



#### 2.2.2 FastDFU模式下接口调用

1. 普通模式升级
```java
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startDfu(ctx, device, file);

        return fastDfu;
```

2. 拷贝模式升级

```java
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startDfuInCopyMode(ctx, device, file, copyAddress);

        return fastDfu;
```

3. 资源文件升级

```java
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startUpdateResource(ctx, device, file, extFlash, writeAddress); 

        return fastDfu;
```

## 3. 使用说明
1. 源码支持的最低版本为Android minSDK 21。
2. 此源码中的所有升级功能均需配套汇顶科技低功耗蓝牙芯片使用。