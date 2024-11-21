package org.dromara.mica.mqtt.server.solon.test.task.task;

import org.dromara.mica.mqtt.server.solon.MqttServerTemplate;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;

/**
 * @author wsq
 */
@Component
public class PublishAllTask {
	@Inject
	private MqttServerTemplate mqttServerTemplate;

	@Scheduled(fixedDelay = 1000)
	public void run() {
		boolean b = mqttServerTemplate.publishAll("/test/123", "mica最牛皮".getBytes(StandardCharsets.UTF_8));
		System.out.println(b);
	}

}
