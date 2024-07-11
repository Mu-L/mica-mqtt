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

package net.dreamlu.iot.mqtt.core.client;

import net.dreamlu.iot.mqtt.codec.*;
import net.dreamlu.iot.mqtt.core.common.MqttPendingPublish;
import net.dreamlu.iot.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.client.ClientChannelContext;
import org.tio.client.TioClient;
import org.tio.client.TioClientConfig;
import org.tio.core.ChannelContext;
import org.tio.core.Node;
import org.tio.core.Tio;
import org.tio.utils.thread.ThreadUtils;
import org.tio.utils.timer.TimerTask;
import org.tio.utils.timer.TimerTaskService;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * mqtt 客户端
 *
 * @author L.cm
 */
public final class MqttClient {
	private static final Logger logger = LoggerFactory.getLogger(MqttClient.class);
	private final TioClient tioClient;
	private final MqttClientCreator config;
	private final TioClientConfig clientTioConfig;
	private final IMqttClientSession clientSession;
	private final TimerTaskService taskService;
	private final ExecutorService mqttExecutor;
	private final IMqttClientMessageIdGenerator messageIdGenerator;
	private ClientChannelContext context;

	public static MqttClientCreator create() {
		return new MqttClientCreator();
	}

	MqttClient(TioClient tioClient, MqttClientCreator config) {
		this.tioClient = tioClient;
		this.config = config;
		this.clientTioConfig = tioClient.getTioClientConfig();
		this.taskService = config.getTaskService();
		this.mqttExecutor = config.getMqttExecutor();
		this.clientSession = config.getClientSession();
		this.messageIdGenerator = config.getMessageIdGenerator();
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public MqttClient subQos0(String topicFilter, IMqttClientMessageListener listener) {
		return subscribe(topicFilter, MqttQoS.QOS0, listener);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public MqttClient subQos1(String topicFilter, IMqttClientMessageListener listener) {
		return subscribe(topicFilter, MqttQoS.QOS1, listener);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public MqttClient subQos2(String topicFilter, IMqttClientMessageListener listener) {
		return subscribe(topicFilter, MqttQoS.QOS2, listener);
	}

	/**
	 * 订阅
	 *
	 * @param mqttQoS     MqttQoS
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public MqttClient subscribe(MqttQoS mqttQoS, String topicFilter, IMqttClientMessageListener listener) {
		return subscribe(topicFilter, mqttQoS, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param mqttQoS     MqttQoS
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @return MqttClient
	 */
	public MqttClient subscribe(String topicFilter, MqttQoS mqttQoS, IMqttClientMessageListener listener) {
		return subscribe(topicFilter, mqttQoS, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param mqttQoS     MqttQoS
	 * @param topicFilter topicFilter
	 * @param listener    MqttMessageListener
	 * @param properties  MqttProperties
	 * @return MqttClient
	 */
	public MqttClient subscribe(String topicFilter, MqttQoS mqttQoS, IMqttClientMessageListener listener, MqttProperties properties) {
		return subscribe(Collections.singletonList(new MqttClientSubscription(mqttQoS, topicFilter, listener)), properties);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilters topicFilter 数组
	 * @param mqttQoS      MqttQoS
	 * @param listener     MqttMessageListener
	 * @return MqttClient
	 */
	public MqttClient subscribe(String[] topicFilters, MqttQoS mqttQoS, IMqttClientMessageListener listener) {
		return subscribe(topicFilters, mqttQoS, listener, null);
	}

	/**
	 * 订阅
	 *
	 * @param topicFilters topicFilter 数组
	 * @param mqttQoS      MqttQoS
	 * @param listener     MqttMessageListener
	 * @param properties   MqttProperties
	 * @return MqttClient
	 */
	public MqttClient subscribe(String[] topicFilters, MqttQoS mqttQoS, IMqttClientMessageListener listener, MqttProperties properties) {
		Objects.requireNonNull(topicFilters, "MQTT subscribe topicFilters is null.");
		List<MqttClientSubscription> subscriptionList = new ArrayList<>();
		for (String topicFilter : topicFilters) {
			subscriptionList.add(new MqttClientSubscription(mqttQoS, topicFilter, listener));
		}
		return subscribe(subscriptionList, properties);
	}

	/**
	 * 批量订阅
	 *
	 * @param subscriptionList 订阅集合
	 * @return MqttClient
	 */
	public MqttClient subscribe(List<MqttClientSubscription> subscriptionList) {
		return subscribe(subscriptionList, null);
	}

	/**
	 * 批量订阅
	 *
	 * @param subscriptionList 订阅集合
	 * @param properties       MqttProperties
	 * @return MqttClient
	 */
	public MqttClient subscribe(List<MqttClientSubscription> subscriptionList, MqttProperties properties) {
		// 1. 先判断是否已经订阅过，重复订阅，直接跳出
		List<MqttClientSubscription> needSubscriptionList = new ArrayList<>();
		for (MqttClientSubscription subscription : subscriptionList) {
			// 校验 topicFilter
			TopicUtil.validateTopicFilter(subscription.getTopicFilter());
			boolean subscribed = clientSession.isSubscribed(subscription);
			if (!subscribed) {
				needSubscriptionList.add(subscription);
			}
		}
		// 2. 已经订阅的跳出
		if (needSubscriptionList.isEmpty()) {
			return this;
		}
		List<MqttTopicSubscription> topicSubscriptionList = needSubscriptionList.stream()
			.map(MqttClientSubscription::toTopicSubscription)
			.collect(Collectors.toList());
		// 3. 没有订阅过
		int messageId = messageIdGenerator.getId();
		MqttSubscribeMessage message = MqttMessageBuilders.subscribe()
			.addSubscriptions(topicSubscriptionList)
			.messageId(messageId)
			.properties(properties)
			.build();
		// 4. 已经连接成功，直接订阅逻辑，未连接成功的添加到订阅列表，连接成功时会重连。
		ClientChannelContext clientContext = getContext();
		if (clientContext != null && clientContext.isAccepted()) {
			boolean result = Tio.send(clientContext, message);
			logger.info("MQTT subscriptionList:{} messageId:{} subscribing result:{}", needSubscriptionList, messageId, result);
			MqttPendingSubscription pendingSubscription = new MqttPendingSubscription(needSubscriptionList, message);
			pendingSubscription.startRetransmitTimer(taskService, (msg) -> Tio.send(clientContext, message));
			clientSession.addPaddingSubscribe(messageId, pendingSubscription);
		} else {
			clientSession.addSubscriptionList(needSubscriptionList);
		}
		return this;
	}

	/**
	 * 取消订阅
	 *
	 * @param topicFilters topicFilter 集合
	 * @return MqttClient
	 */
	public MqttClient unSubscribe(String... topicFilters) {
		return unSubscribe(Arrays.asList(topicFilters));
	}

	/**
	 * 取消订阅
	 *
	 * @param topicFilters topicFilter 集合
	 * @return MqttClient
	 */
	public MqttClient unSubscribe(List<String> topicFilters) {
		// 1. 校验 topicFilter
		TopicUtil.validateTopicFilter(topicFilters);
		// 2. 优先取消本地订阅
		clientSession.removePaddingSubscribes(topicFilters);
		clientSession.removeSubscriptions(topicFilters);
		// 3. 发送取消订阅到服务端
		int messageId = messageIdGenerator.getId();
		MqttUnsubscribeMessage message = MqttMessageBuilders.unsubscribe()
			.addTopicFilters(topicFilters)
			.messageId(messageId)
			.build();
		MqttPendingUnSubscription pendingUnSubscription = new MqttPendingUnSubscription(topicFilters, message);
		boolean result = Tio.send(getContext(), message);
		logger.info("MQTT Topic:{} messageId:{} unSubscribing result:{}", topicFilters, messageId, result);
		// 4. 启动取消订阅线程
		clientSession.addPaddingUnSubscribe(messageId, pendingUnSubscription);
		pendingUnSubscription.startRetransmissionTimer(taskService, msg -> Tio.send(getContext(), msg));
		return this;
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息内容
	 * @return 是否发送成功
	 */
	public boolean publish(String topic, byte[] payload) {
		return publish(topic, payload, MqttQoS.QOS0);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息内容
	 * @param qos     MqttQoS
	 * @return 是否发送成功
	 */
	public boolean publish(String topic, byte[] payload, MqttQoS qos) {
		return publish(topic, payload, qos, false);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息内容
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publish(String topic, byte[] payload, boolean retain) {
		return publish(topic, payload, MqttQoS.QOS0, retain);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publish(String topic, byte[] payload, MqttQoS qos, boolean retain) {
		return publish(topic, payload, qos, (publishBuilder) -> publishBuilder.retained(retain));
	}

	/**
	 * 发布消息
	 *
	 * @param topic      topic
	 * @param payload    消息体
	 * @param qos        MqttQoS
	 * @param retain     是否在服务器上保留消息
	 * @param properties MqttProperties
	 * @return 是否发送成功
	 */
	public boolean publish(String topic, byte[] payload, MqttQoS qos, boolean retain, MqttProperties properties) {
		return publish(topic, payload, qos, (publishBuilder) -> publishBuilder.retained(retain).properties(properties));
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param builder PublishBuilder
	 * @return 是否发送成功
	 */
	public boolean publish(String topic, byte[] payload, MqttQoS qos, Consumer<MqttMessageBuilders.PublishBuilder> builder) {
		// 校验 topic
		TopicUtil.validateTopicName(topic);
		// qos 判断
		boolean isHighLevelQoS = MqttQoS.QOS1 == qos || MqttQoS.QOS2 == qos;
		int messageId = isHighLevelQoS ? messageIdGenerator.getId() : -1;
		MqttMessageBuilders.PublishBuilder publishBuilder = MqttMessageBuilders.publish();
		// 自定义配置
		builder.accept(publishBuilder);
		// 内置配置
		publishBuilder.topicName(topic)
			.payload(payload)
			.messageId(messageId)
			.qos(qos);
		MqttPublishMessage message = publishBuilder.build();
		ClientChannelContext clientContext = getContext();
		if (clientContext == null) {
			logger.error("MQTT client publish fail, TCP not connected.");
			return false;
		}
		// 如果已经连接成功，但是还没有 mqtt 认证，则进行休眠等待
		if (!clientContext.isClosed()) {
			while (!clientContext.isAccepted()) {
				ThreadUtils.sleep(10);
			}
		}
		// 发送消息
		boolean result = Tio.send(clientContext, message);
		logger.debug("MQTT Topic:{} qos:{} retain:{} publish result:{}", topic, qos, publishBuilder.isRetained(), result);
		if (isHighLevelQoS) {
			MqttPendingPublish pendingPublish = new MqttPendingPublish(payload, message, qos);
			clientSession.addPendingPublish(messageId, pendingPublish);
			pendingPublish.startPublishRetransmissionTimer(taskService, msg -> Tio.send(clientContext, msg));
		}
		return result;
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay) {
		return schedule(command, delay, null);
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay, Executor executor) {
		return config.getTaskService().addTask((systemTimer -> new TimerTask(delay) {
			@Override
			public void run() {
				try {
					// 1. 再次添加 任务
					systemTimer.add(this);
					// 2. 执行任务
					if (executor == null) {
						command.run();
					} else {
						executor.execute(command);
					}
				} catch (Exception e) {
					logger.error("Mqtt client schedule error", e);
				}
			}
		}));
	}

	/**
	 * 添加定时任务
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay) {
		return scheduleOnce(command, delay, null);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay, Executor executor) {
		return config.getTaskService().addTask((systemTimer -> new TimerTask(delay) {
			@Override
			public void run() {
				try {
					if (executor == null) {
						command.run();
					} else {
						executor.execute(command);
					}
				} catch (Exception e) {
					logger.error("Mqtt client schedule once error", e);
				}
			}
		}));
	}

	/**
	 * 异步连接
	 *
	 * @return TioClient
	 */
	MqttClient start(boolean sync) {
		// 启动 tio
		Node node = new Node(config.getIp(), config.getPort());
		try {
			if (sync) {
				this.tioClient.connect(node, config.getTimeout());
			} else {
				this.tioClient.asyncConnect(node, config.getTimeout());
			}
			return this;
		} catch (Exception e) {
			throw new IllegalStateException("Mica mqtt client async start fail.", e);
		}
	}

	/**
	 * 重连
	 */
	public void reconnect() {
		ClientChannelContext channelContext = getContext();
		if (channelContext == null) {
			return;
		}
		try {
			// 判断是否 removed
			if (channelContext.isRemoved()) {
				channelContext.setRemoved(false);
			}
			tioClient.reconnect(channelContext, config.getTimeout());
		} catch (Exception e) {
			logger.error("mqtt client reconnect error", e);
		}
	}

	/**
	 * 重连到新的服务端节点
	 *
	 * @param ip   ip
	 * @param port port
	 * @return 是否成功
	 */
	public boolean reconnect(String ip, int port) {
		return reconnect(new Node(ip, port));
	}

	/**
	 * 重连到新的服务端节点
	 *
	 * @param serverNode Node
	 * @return 是否成功
	 */
	public boolean reconnect(Node serverNode) {
		// 更新 ip 和端口
		this.config.ip(serverNode.getIp()).port(serverNode.getPort());
		// 获取老的
		ClientChannelContext oldContext = getContext();
		if (oldContext != null) {
			Tio.remove(context, "切换服务地址：" + serverNode);
		}
		try {
			this.context = tioClient.connect(serverNode, config.getTimeout());
			return true;
		} catch (Exception e) {
			logger.error("mqtt client reconnect error", e);
		}
		return false;
	}

	/**
	 * 断开 mqtt 连接
	 *
	 * @return 是否成功
	 */
	public boolean disconnect() {
		ClientChannelContext channelContext = getContext();
		if (channelContext == null) {
			return false;
		}
		boolean result = Tio.bSend(channelContext, MqttMessage.DISCONNECT);
		if (result) {
			Tio.close(channelContext, null, "MqttClient disconnect.", true);
		}
		return result;
	}

	/**
	 * 停止客户端
	 *
	 * @return 是否停止成功
	 */
	public boolean stop() {
		// 1. 断开连接
		this.disconnect();
		// 2. 停止 tio
		boolean result = tioClient.stop();
		// 3. 停止工作线程
		try {
			mqttExecutor.shutdown();
		} catch (Exception e1) {
			logger.error(e1.getMessage(), e1);
		}
		try {
			// 等待线程池中的任务结束，客户端等待 6 秒基本上足够了
			result &= mqttExecutor.awaitTermination(6, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.error(e.getMessage(), e);
		}
		logger.info("MqttClient stop result:{}", result);
		// 4. 清理 session
		this.clientSession.clean();
		return result;
	}

	/**
	 * 获取 TioClient
	 *
	 * @return TioClient
	 */
	public TioClient getTioClient() {
		return tioClient;
	}

	/**
	 * 获取配置
	 *
	 * @return MqttClientCreator
	 */
	public MqttClientCreator getClientCreator() {
		return config;
	}

	/**
	 * 获取 ClientTioConfig
	 *
	 * @return ClientTioConfig
	 */
	public TioClientConfig getClientTioConfig() {
		return clientTioConfig;
	}

	/**
	 * 获取 ClientChannelContext
	 *
	 * @return ClientChannelContext
	 */
	public ClientChannelContext getContext() {
		if (context != null) {
			return context;
		}
		synchronized (this) {
			if (context == null) {
				Set<ChannelContext> contextSet = Tio.getConnecteds(clientTioConfig);
				if (contextSet != null && !contextSet.isEmpty()) {
					this.context = (ClientChannelContext) contextSet.iterator().next();
				}
			}
		}
		return this.context;
	}

	/**
	 * 判断客户端跟服务端是否连接
	 *
	 * @return 是否已经连接成功
	 */
	public boolean isConnected() {
		ClientChannelContext channelContext = getContext();
		return channelContext != null && channelContext.isAccepted();
	}

	/**
	 * 判断客户端跟服务端是否断开连接
	 *
	 * @return 是否断连
	 */
	public boolean isDisconnected() {
		return !isConnected();
	}

}
