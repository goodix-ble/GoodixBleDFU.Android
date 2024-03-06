# Android Integration Guide for Goodix BLE DFU SDK

[EN](README.md)   |  [中文](README_zh.md)



## 1. Introduction
Android DFU SDK is provided by Goodix Tech. Co. LTd, and is mainly used for fireware program update of BLE Chips developed by Goodix Tech. This SDK is fully open source and allow users to integrate it to their android app projects.

## 2. Integration Step

### 2.1 Import to project
Import the libcom and libdfu2 modules from the SDK source code and load them into the project. The description is as following:
- libcom: The module contains commonly used tool classes. Mainly used for libdfu2 and needs to be imported during integration.
- Libdfu2: The module contains classes related to DFU upgrade and supports two DFU upgrade modes:
  - **DFU v2 mode**: It involves the EasyDfu2 class and is the latest developed mode. It is recommended for users to use
  - **FastDFU mode**: It involves the FastDFU class, a traditional upgrade mode that needs to be imported during integration and is not recommended for new users.
- app: Project Application main program, which implements a concise DFU upgrade application without import, but it is recommended that users refer to its implementation when integrating, especially the MainActivity and TestCase classes.

### 2.2 How to Call DFU API

#### 2.2.1 Call in DFU v2 mode

1. Update in **Normal Mode**
```java
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startDfu(ctx, device, file);
```
2. Update in **Copy in Two Bank Mode** 
```java
        final EasyDfu2 dfu2 = new EasyDfu2();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        dfu2.setLogger(ramLogger);
        dfu2.setListener(listener);
        dfu2.setFastMode(fastMode);
        dfu2.startDfuInCopyMode(ctx, device, file, copyAddress); 
```

3. Update the Resource
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



#### 2.2.2 Call in FastDFU mode

1. Update in **Normal Mode**

```java
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startDfu(ctx, device, file);

        return fastDfu;
```

2. Update in **Copy Mode**

```java
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startDfuInCopyMode(ctx, device, file, copyAddress);

        return fastDfu;
```

3. Update the Resource

```java
        final FastDfu fastDfu = new FastDfu();

        ramLogger.clearBuffer();
        ramLogger.setNextLogger(LogcatLogger.INSTANCE);
        fastDfu.setLogger(ramLogger);
        fastDfu.setListener(listener);
        fastDfu.startUpdateResource(ctx, device, file, extFlash, writeAddress); 

        return fastDfu;
```

## 3. Usage Note
1. Minimum version requirement:  Android minSDK 21。
2. SDK is developed for Goodix BLE SoC, Not suitable for other chips

## 4. Change Notes
- Please refer to [Change Notes](../../wiki/Change-Notes)

