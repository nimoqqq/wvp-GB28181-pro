package com.genersoft.iot.vmp.gb28181;

import com.genersoft.iot.vmp.conf.SipConfig;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.gb28181.conf.DefaultProperties;
import com.genersoft.iot.vmp.gb28181.transmit.ISIPProcessorObserver;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(value = 10)
public class SipLayer implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(SipLayer.class);

    @Autowired
    private SipConfig sipConfig;

    @Autowired
    private ISIPProcessorObserver sipProcessorObserver;

    @Autowired
    private UserSetting userSetting;

	/**
	 * 创建SipProvider对象, 进行 SIP 请求和响应的发送和接收操作 TCP
	 */
    private final Map<String, SipProviderImpl> tcpSipProviderMap = new ConcurrentHashMap<>();
    private final Map<String, SipProviderImpl> udpSipProviderMap = new ConcurrentHashMap<>();

    @Override
    public void run(String... args) {
        // 服务端监听端口
        List<String> monitorIps = new ArrayList<>();
        // 使用逗号分割多个ip
        String separator = ",";
        if (sipConfig.getIp().indexOf(separator) > 0) {
            String[] split = sipConfig.getIp().split(separator);
            monitorIps.addAll(Arrays.asList(split));
        } else {
            monitorIps.add(sipConfig.getIp());
        }
        // 设置 SIP 实现的名称, 即 gov.nist
        // SIP 实现是一种 SIP 协议的具体实现,通过设置 SIP 实现的名称，可以告诉 SIP API 使用哪个具体的实现来处理 SIP 请求和响应
        // 设置 SIP 实现的名称为 gov.nist，表示使用 JAIN-SIP 作为 SIP 实现
        SipFactory.getInstance().setPathName("gov.nist");
        if (monitorIps.size() > 0) {
            for (String monitorIp : monitorIps) {
                addListeningPoint(monitorIp, sipConfig.getPort());
            }
            if (udpSipProviderMap.size() + tcpSipProviderMap.size() == 0) {
                System.exit(1);
            }
        }
    }

    /**
     * @param monitorIp 监听端口
     * @param port      28181服务监听的端口
     */
    private void addListeningPoint(String monitorIp, int port) {
		// 创建一个 SIP 协议栈对象,
        SipStackImpl sipStack;
        try {
			// 创建一个 SIP 协议栈对象，并使用指定的属性配置对其进行初始化
            sipStack = (SipStackImpl) SipFactory.getInstance().createSipStack(DefaultProperties.getProperties(monitorIp, userSetting.getSipLog()));
        } catch (PeerUnavailableException e) {
            logger.error("[Sip Server] SIP服务启动失败， 监听地址{}失败,请检查ip是否正确", monitorIp);
            return;
        }

        try {
			// 创建一个 SIP 监听点对象，用于接收 SIP 请求
            ListeningPoint tcpListeningPoint = sipStack.createListeningPoint(monitorIp, port, "TCP");
			// 绑定监听点,创建SipProvider对象, 进行 SIP 请求和响应的发送和接收操作
            SipProviderImpl tcpSipProvider = (SipProviderImpl) sipStack.createSipProvider(tcpListeningPoint);

			// 设置是否自动处理对话错误,默认不处理
            tcpSipProvider.setDialogErrorsAutomaticallyHandled();
			// 将一个 SipListener 实例添加到 SipProvider 实例中，以接收 SIP 消息事件
            tcpSipProvider.addSipListener(sipProcessorObserver);
            tcpSipProviderMap.put(monitorIp, tcpSipProvider);

            logger.info("[Sip Server] tcp://{}:{} 启动成功", monitorIp, port);
        } catch (TransportNotSupportedException
                | TooManyListenersException
                | ObjectInUseException
                | InvalidArgumentException e) {
            logger.error("[Sip Server] tcp://{}:{} SIP服务启动失败,请检查端口是否被占用或者ip是否正确"
                    , monitorIp, port);
        }

        try {
            ListeningPoint udpListeningPoint = sipStack.createListeningPoint(monitorIp, port, "UDP");

            SipProviderImpl udpSipProvider = (SipProviderImpl) sipStack.createSipProvider(udpListeningPoint);
            udpSipProvider.addSipListener(sipProcessorObserver);

            udpSipProviderMap.put(monitorIp, udpSipProvider);

            logger.info("[Sip Server] udp://{}:{} 启动成功", monitorIp, port);
        } catch (TransportNotSupportedException
                | TooManyListenersException
                | ObjectInUseException
                | InvalidArgumentException e) {
            logger.error("[Sip Server] udp://{}:{} SIP服务启动失败,请检查端口是否被占用或者ip是否正确"
                    , monitorIp, port);
        }
    }

    public SipProviderImpl getUdpSipProvider(String ip) {
        if (ObjectUtils.isEmpty(ip)) {
            return null;
        }
        return udpSipProviderMap.get(ip);
    }

    public SipProviderImpl getUdpSipProvider() {
        if (udpSipProviderMap.size() != 1) {
            return null;
        }
        return udpSipProviderMap.values().stream().findFirst().get();
    }

    public SipProviderImpl getTcpSipProvider() {
        if (tcpSipProviderMap.size() != 1) {
            return null;
        }
        return tcpSipProviderMap.values().stream().findFirst().get();
    }

    public SipProviderImpl getTcpSipProvider(String ip) {
        if (ObjectUtils.isEmpty(ip)) {
            return null;
        }
        return tcpSipProviderMap.get(ip);
    }

    public String getLocalIp(String deviceLocalIp) {
        if (!ObjectUtils.isEmpty(deviceLocalIp)) {
            return deviceLocalIp;
        }
        return getUdpSipProvider().getListeningPoint().getIPAddress();
    }
}
