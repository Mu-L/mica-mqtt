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

package net.dreamlu.iot.mqtt.core.common;

import net.dreamlu.iot.mqtt.codec.MqttQoS;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * mqtt 死信消息
 *
 * @author L.cm
 */
public class DeadLetterMessage implements Serializable {
	private String topic;
	private MqttQoS qos;
	private byte[] payload;
	private boolean retain;

	public String getTopic() {
		return topic;
	}

	public DeadLetterMessage setTopic(String topic) {
		this.topic = topic;
		return this;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public DeadLetterMessage setQos(MqttQoS qos) {
		this.qos = qos;
		return this;
	}

	public byte[] getPayload() {
		return payload;
	}

	public DeadLetterMessage setPayload(byte[] payload) {
		this.payload = payload;
		return this;
	}

	public boolean isRetain() {
		return retain;
	}

	public DeadLetterMessage setRetain(boolean retain) {
		this.retain = retain;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DeadLetterMessage)) {
			return false;
		}
		DeadLetterMessage that = (DeadLetterMessage) o;
		return retain == that.retain &&
			Objects.equals(topic, that.topic) &&
			qos == that.qos &&
			Arrays.equals(payload, that.payload);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(topic, qos, retain);
		result = 31 * result + Arrays.hashCode(payload);
		return result;
	}

	@Override
	public String toString() {
		return "DeadLetterMessage{" +
			"topic='" + topic + '\'' +
			", qos=" + qos +
			", payload=" + Arrays.toString(payload) +
			", retain=" + retain +
			'}';
	}
}
