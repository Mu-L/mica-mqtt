open module net.dreamlu.mica.mqtt.server.solon.plugin {
	requires solon;
	requires lombok;
	requires transitive net.dreamlu.mica.mqtt.server;
	exports org.dromara.mica.mqtt.server.noear;
	exports org.dromara.mica.mqtt.server.solon.event;
	exports org.dromara.mica.mqtt.server.solon.config;
	provides org.noear.solon.core.Plugin with org.dromara.mica.mqtt.server.solon.integration.MqttServerPluginImpl;
}
