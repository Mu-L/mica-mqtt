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

package org.dromara.mica.mqtt.client.solon.listener;

import org.dromara.mica.mqtt.client.solon.event.MqttDisconnectEvent;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.core.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端连接状态监听
 *
 * @author L.cm
 */
@Component
public class MqttClientDisconnectListener implements EventListener<MqttDisconnectEvent> {
	private static final Logger logger = LoggerFactory.getLogger(MqttClientDisconnectListener.class);

	@Inject
	private MqttClientCreator mqttClientCreator;

	@Override
	public void onEvent(MqttDisconnectEvent mqttDisconnectEvent) throws Throwable {
		logger.info("MqttDisconnectEvent:{}", mqttDisconnectEvent);
		// 在断线时更新 clientId、username、password，只能改这 3 个，不可调用其他方法。
//		mqttClientCreator.clientId("newClient" + System.currentTimeMillis())
//			.username("newUserName")
//			.password("newPassword");
	}
}
