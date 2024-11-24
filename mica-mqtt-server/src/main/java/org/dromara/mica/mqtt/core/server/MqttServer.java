/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.core.server;

import org.dromara.mica.mqtt.codec.MqttMessageBuilders;
import org.dromara.mica.mqtt.codec.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.server.TioServer;
import org.tio.server.TioServerConfig;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.timer.TimerTask;
import org.tio.utils.timer.TimerTaskService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * mqtt 服务端
 *
 * @author L.cm
 */
public final class MqttServer {
	private static final Logger logger = LoggerFactory.getLogger(MqttServer.class);
	private final TioServer tioServer;
	private final org.dromara.mica.mqtt.core.server.http.core.MqttWebServer webServer;
	private final MqttServerCreator serverCreator;
	private final IMqttSessionManager sessionManager;
	private final org.dromara.mica.mqtt.core.server.store.IMqttMessageStore messageStore;
	/**
	 * taskService
	 */
	private final TimerTaskService taskService;

	MqttServer(TioServer tioServer,
			   org.dromara.mica.mqtt.core.server.http.core.MqttWebServer webServer,
			   MqttServerCreator serverCreator,
			   TimerTaskService taskService) {
		this.tioServer = tioServer;
		this.webServer = webServer;
		this.serverCreator = serverCreator;
		this.sessionManager = serverCreator.getSessionManager();
		this.messageStore = serverCreator.getMessageStore();
		this.taskService = taskService;
	}

	public static MqttServerCreator create() {
		return new MqttServerCreator();
	}

	/**
	 * 获取 TioServer
	 *
	 * @return TioServer
	 */
	public TioServer getTioServer() {
		return this.tioServer;
	}

	/**
	 * 获取 http、websocket 服务
	 *
	 * @return MqttWebServer
	 */
	public org.dromara.mica.mqtt.core.server.http.core.MqttWebServer getWebServer() {
		return webServer;
	}

	/**
	 * 获取 ServerTioConfig
	 *
	 * @return the serverTioConfig
	 */
	public TioServerConfig getServerConfig() {
		return this.tioServer.getServerConfig();
	}

	/**
	 * 获取 mqtt 配置
	 *
	 * @return MqttServerCreator
	 */
	public MqttServerCreator getServerCreator() {
		return serverCreator;
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, byte[] payload) {
		return publish(clientId, topic, payload, MqttQoS.QOS0);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, byte[] payload, MqttQoS qos) {
		return publish(clientId, topic, payload, qos, false);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, byte[] payload, boolean retain) {
		return publish(clientId, topic, payload, MqttQoS.QOS0, retain);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, byte[] payload, MqttQoS qos, boolean retain) {
		// 校验 topic
		TopicUtil.validateTopicName(topic);
		// 存储保留消息
		if (retain) {
			this.saveRetainMessage(topic, qos, payload);
		}
		// 获取 context
		ChannelContext context = Tio.getByBsId(getServerConfig(), clientId);
		if (context == null || context.isClosed()) {
			logger.warn("Mqtt Topic:{} publish to clientId:{} ChannelContext is null may be disconnected.", topic, clientId);
			return false;
		}
		Integer subMqttQoS = sessionManager.searchSubscribe(topic, clientId);
		if (subMqttQoS == null) {
			logger.warn("Mqtt Topic:{} publish but clientId:{} not subscribed.", topic, clientId);
			return false;
		}
		MqttQoS mqttQoS = qos.value() > subMqttQoS ? MqttQoS.valueOf(subMqttQoS) : qos;
		return publish(context, clientId, topic, payload, mqttQoS, retain);
	}

	/**
	 * 发布消息
	 *
	 * @param context  ChannelContext
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	private boolean publish(ChannelContext context, String clientId, String topic, byte[] payload, MqttQoS qos, boolean retain) {
		boolean isHighLevelQoS = MqttQoS.QOS1 == qos || MqttQoS.QOS2 == qos;
		int messageId = isHighLevelQoS ? sessionManager.getMessageId(clientId) : -1;
		MqttPublishMessage message = MqttMessageBuilders.publish()
			.topicName(topic)
			.payload(payload)
			.qos(qos)
			.retained(retain)
			.messageId(messageId)
			.build();
		boolean result = Tio.send(context, message);
		logger.debug("MQTT Topic:{} qos:{} retain:{} publish clientId:{} result:{}", topic, qos, retain, clientId, result);
		if (isHighLevelQoS) {
			MqttPendingPublish pendingPublish = new MqttPendingPublish(payload, message, qos);
			sessionManager.addPendingPublish(clientId, messageId, pendingPublish);
			pendingPublish.startPublishRetransmissionTimer(taskService, context);
		}
		return result;
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, byte[] payload) {
		return publishAll(topic, payload, MqttQoS.QOS0);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, byte[] payload, MqttQoS qos) {
		return publishAll(topic, payload, qos, false);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, byte[] payload, boolean retain) {
		return publishAll(topic, payload, MqttQoS.QOS0, retain);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, byte[] payload, MqttQoS qos, boolean retain) {
		// 校验 topic
		TopicUtil.validateTopicName(topic);
		// 存储保留消息
		if (retain) {
			this.saveRetainMessage(topic, qos, payload);
		}
		// 查找订阅该 topic 的客户端
		List<org.dromara.mica.mqtt.core.server.model.Subscribe> subscribeList = sessionManager.searchSubscribe(topic);
		if (subscribeList.isEmpty()) {
			logger.debug("Mqtt Topic:{} publishAll but subscribe client list is empty.", topic);
			return false;
		}
		for (org.dromara.mica.mqtt.core.server.model.Subscribe subscribe : subscribeList) {
			String clientId = subscribe.getClientId();
			ChannelContext context = Tio.getByBsId(getServerConfig(), clientId);
			if (context == null || context.isClosed()) {
				logger.warn("Mqtt Topic:{} publish to clientId:{} channel is null may be disconnected.", topic, clientId);
				continue;
			}
			int subMqttQoS = subscribe.getMqttQoS();
			MqttQoS mqttQoS = qos.value() > subMqttQoS ? MqttQoS.valueOf(subMqttQoS) : qos;
			publish(context, clientId, topic, payload, mqttQoS, false);
		}
		return true;
	}

	/**
	 * 发送消息到客户端
	 *
	 * @param topic   topic
	 * @param message Message
	 * @return 是否成功
	 */
	public boolean sendToClient(String topic, org.dromara.mica.mqtt.core.server.model.Message message) {
		// 客户端id
		String clientId = message.getClientId();
		MqttQoS mqttQoS = MqttQoS.valueOf(message.getQos());
		if (StrUtil.isBlank(clientId)) {
			return publishAll(topic, message.getPayload(), mqttQoS, message.isRetain());
		} else {
			return publish(clientId, topic, message.getPayload(), mqttQoS, message.isRetain());
		}
	}

	/**
	 * 存储保留消息
	 *
	 * @param topic   topic
	 * @param mqttQoS MqttQoS
	 * @param payload ByteBuffer
	 */
	private void saveRetainMessage(String topic, MqttQoS mqttQoS, byte[] payload) {
		org.dromara.mica.mqtt.core.server.model.Message retainMessage = new org.dromara.mica.mqtt.core.server.model.Message();
		retainMessage.setTopic(topic);
		retainMessage.setQos(mqttQoS.value());
		retainMessage.setPayload(payload);
		retainMessage.setMessageType(org.dromara.mica.mqtt.core.server.enums.MessageType.DOWN_STREAM);
		retainMessage.setRetain(true);
		retainMessage.setDup(false);
		retainMessage.setTimestamp(System.currentTimeMillis());
		retainMessage.setNode(serverCreator.getNodeName());
		this.messageStore.addRetainMessage(topic, retainMessage);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay) {
		return this.tioServer.schedule(command, delay);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay, Executor executor) {
		return this.tioServer.schedule(command, delay, executor);
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay) {
		return this.tioServer.scheduleOnce(command, delay, null);
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay, Executor executor) {
		return this.tioServer.scheduleOnce(command, delay, executor);
	}

	/**
	 * 获取 ChannelContext
	 *
	 * @param clientId clientId
	 * @return ChannelContext
	 */
	public ChannelContext getChannelContext(String clientId) {
		return Tio.getByBsId(getServerConfig(), clientId);
	}

	/**
	 * 服务端主动断开连接
	 *
	 * @param clientId clientId
	 */
	public void close(String clientId) {
		Tio.remove(getChannelContext(clientId), "Mqtt server close this connects.");
	}

	/**
	 * 启动服务
	 *
	 * @return 是否启动
	 */
	public boolean start() {
		// 1. 启动 mqtt tcp
		try {
			tioServer.start(this.serverCreator.getIp(), this.serverCreator.getPort());
		} catch (IOException e) {
			String message = String.format("Mica mqtt tcp server port %d start fail.", this.serverCreator.getPort());
			throw new IllegalStateException(message, e);
		}
		// 2. 启动 mqtt web
		if (webServer != null) {
			try {
				webServer.start();
			} catch (IOException e) {
				String message = String.format("Mica mqtt http/websocket server port %d start fail.", this.serverCreator.getWebPort());
				throw new IllegalStateException(message, e);
			}
		}
		return true;
	}

	/**
	 * 停止服务
	 *
	 * @return 是否停止
	 */
	public boolean stop() {
		// 停止服务
		boolean result = this.tioServer.stop();
		logger.info("Mqtt tcp server stop result:{}", result);
		if (webServer != null) {
			result &= webServer.stop();
			logger.info("Mqtt websocket server stop result:{}", result);
		}
		// 停止工作线程
		ExecutorService mqttExecutor = serverCreator.getMqttExecutor();
		try {
			mqttExecutor.shutdown();
		} catch (Exception e1) {
			logger.error(e1.getMessage(), e1);
		}
		try {
			// 等待线程池中的任务结束，等待 10 分钟
			result &= mqttExecutor.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error(e.getMessage(), e);
		}
		try {
			sessionManager.clean();
		} catch (Throwable e) {
			logger.error("MqttServer stop session clean error.", e);
		}
		return result;
	}

}