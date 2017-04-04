
package com.beeva.kura.serialthing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.comm.CommConnection;
import org.eclipse.kura.comm.CommURI;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.osgi.service.io.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialThing implements ConfigurableComponent, CloudClientListener {

	private static final Logger s_logger = LoggerFactory.getLogger(SerialThing.class);
	private static final String APP_ID = "com.beeva.kura.serialthing.SerialThing";

	private static final String SERIAL_DEVICE_PROP_NAME = "serial.device";
	private static final String SERIAL_BAUDRATE_PROP_NAME = "serial.baudrate";
	private static final String SERIAL_DATA_BITS_PROP_NAME = "serial.data-bits";
	private static final String SERIAL_PARITY_PROP_NAME = "serial.parity";
	private static final String SERIAL_STOP_BITS_PROP_NAME = "serial.stop-bits";
	private static final String SERIAL_FLOWCONTROL_PROP_NAME = "serial.flowcontrol";
	private static final String PUBLISH_RATE_PROP_NAME = "publish.rate";
	private static final String PUBLISH_TOPIC_PROP_NAME = "publish.semanticTopic";
	private static final String PUBLISH_QOS_PROP_NAME = "publish.qos";
	private static final String PUBLISH_RETAIN_PROP_NAME = "publish.retain";

	private int temperature;
	private int humidity;

	private ConnectionFactory m_connectionFactory;
	private CommConnection m_commConnection;
	private InputStream m_commIs;
	private OutputStream m_commOs;

	private ScheduledThreadPoolExecutor m_serial_worker;
	private Future<?> m_serial_handle;

	private ScheduledExecutorService m_mqtt_worker;
	private Future<?> m_mqtt_handle;

	private CloudService m_cloudService;
	private CloudClient m_cloudClient;

	private Map<String, Object> m_properties;

	// ----------------------------------------------------------------
	//
	// Dependencies
	//
	// ----------------------------------------------------------------
	public void setCloudService(CloudService cloudService) {
		this.m_cloudService = cloudService;
	}

	public void unsetCloudService(CloudService cloudService) {
		this.m_cloudService = null;
	}

	public void setConnectionFactory(ConnectionFactory connectionFactory) {
		this.m_connectionFactory = connectionFactory;
	}

	public void unsetConnectionFactory(ConnectionFactory connectionFactory) {
		this.m_connectionFactory = null;
	}

	// ----------------------------------------------------------------
	//
	// Activation APIs
	//
	// ----------------------------------------------------------------

	protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
		s_logger.info("Activating " + APP_ID);
		this.m_mqtt_worker = Executors.newSingleThreadScheduledExecutor();
		this.m_serial_worker = new ScheduledThreadPoolExecutor(1);
		this.m_properties = new HashMap<String, Object>();
		doUpdate(properties);
		try {
			// Acquire a Cloud Application Client for this Application
			s_logger.info(APP_ID + " Getting CloudClient");
			this.m_cloudClient = this.m_cloudService.newCloudClient(APP_ID);
			this.m_cloudClient.addCloudClientListener(this);
		} catch (Exception e) {
			s_logger.error(APP_ID + " Error during component activation: ", e);
			throw new ComponentException(e);
		}
		s_logger.info("Activating " + APP_ID + "Done.");
	}

	protected void deactivate(ComponentContext componentContext) {
		s_logger.info("Deactivating " + APP_ID);
		// shutting down the worker and cleaning up the properties
		m_serial_handle.cancel(true);
		m_serial_worker.shutdownNow();
		m_mqtt_handle.cancel(true);
		m_mqtt_worker.shutdownNow();
		// close the serial port
		closePort();
		s_logger.info("Deactivating  " + APP_ID + "Done.");
	}

	public void updated(Map<String, Object> properties) {
		s_logger.info("Updating " + APP_ID);
		doUpdate(properties);
		s_logger.info("Updating " + APP_ID);
	}

	// ----------------------------------------------------------------
	//
	// Cloud Application Callback Methods
	//
	// ----------------------------------------------------------------

	@Override
	public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
	}

	@Override
	public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
	}

	@Override
	public void onConnectionLost() {
	}

	@Override
	public void onConnectionEstablished() {
	}

	@Override
	public void onMessageConfirmed(int messageId, String appTopic) {
	}

	@Override
	public void onMessagePublished(int messageId, String appTopic) {
	}

	// ----------------------------------------------------------------
	//
	// Private Methods
	//
	// ----------------------------------------------------------------

	/**
	 * Called after a new set of properties has been configured on the service
	 */
	private void doUpdate(Map<String, Object> properties) {
		try {
			for (String s : properties.keySet()) {
				s_logger.info("Update - " + s + ": " + properties.get(s));
			}
			// cancel current workers if active
			if (m_serial_handle != null) {
				m_serial_handle.cancel(true);
			}
			if (m_mqtt_handle != null) {
				m_mqtt_handle.cancel(true);
			}
			// close the serial port so it can be reconfigured
			closePort();
			// store the properties
			m_properties.clear();
			m_properties.putAll(properties);
			// reopen the port with the new configuration
			openPort();
			// start the workers thread
			m_serial_handle = m_serial_worker.submit(new Runnable() {
				@Override
				public void run() {
					doSerial();
				}
			});
			int pubrate = (Integer) this.m_properties.get(PUBLISH_RATE_PROP_NAME);
			this.m_mqtt_handle = this.m_mqtt_worker.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					Thread.currentThread().setName(getClass().getSimpleName());
					doPublish();
				}
			}, 0, pubrate, TimeUnit.SECONDS);
		} catch (Throwable t) {
			s_logger.error("Unexpected Throwable", t);
		}
	}

	private void openPort() {
		String port = (String) m_properties.get(SERIAL_DEVICE_PROP_NAME);

		if (port == null) {
			s_logger.info("Port name not configured");
			return;
		}
		int baudRate = Integer.valueOf((String) m_properties.get(SERIAL_BAUDRATE_PROP_NAME));
		int dataBits = Integer.valueOf((String) m_properties.get(SERIAL_DATA_BITS_PROP_NAME));
		int stopBits = Integer.valueOf((String) m_properties.get(SERIAL_STOP_BITS_PROP_NAME));

		String sParity = (String) m_properties.get(SERIAL_PARITY_PROP_NAME);

		int parity = CommURI.PARITY_NONE;
		if (sParity.equals("odd")) {
			parity = CommURI.PARITY_ODD;
		} else if (sParity.equals("even")) {
			parity = CommURI.PARITY_EVEN;
		}

		String sFlowcontrol = (String) m_properties.get(SERIAL_FLOWCONTROL_PROP_NAME);
		int flowcontrol = CommURI.FLOWCONTROL_NONE;
		if (sFlowcontrol.equals("rtscts")) {
			flowcontrol = CommURI.FLOWCONTROL_RTSCTS_IN + CommURI.FLOWCONTROL_RTSCTS_OUT;
		} else if (sFlowcontrol.equals("xonxoff")) {
			flowcontrol = CommURI.FLOWCONTROL_XONXOFF_IN + CommURI.FLOWCONTROL_XONXOFF_OUT;
		}

		String uri = new CommURI.Builder(port).withBaudRate(baudRate).withDataBits(dataBits).withStopBits(stopBits)
				.withParity(parity).withTimeout(1000).withFlowControl(flowcontrol).build().toString();
		try {
			m_commConnection = (CommConnection) m_connectionFactory.createConnection(uri, 1, false);
			m_commIs = m_commConnection.openInputStream();
			m_commOs = m_commConnection.openOutputStream();
			s_logger.info(port + " open");
		} catch (IOException e) {
			s_logger.error("Failed to open port " + port, e);
			cleanupPort();
		}
	}

	private void cleanupPort() {
		if (m_commIs != null) {
			try {
				s_logger.info("Closing port input stream...");
				m_commIs.close();
				s_logger.info("Closed port input stream");
			} catch (IOException e) {
				s_logger.error("Cannot close port input stream", e);
			}
			m_commIs = null;
		}
		if (m_commOs != null) {
			try {
				s_logger.info("Closing port output stream...");
				m_commOs.close();
				s_logger.info("Closed port output stream");
			} catch (IOException e) {
				s_logger.error("Cannot close port output stream", e);
			}
			m_commOs = null;
		}
		if (m_commConnection != null) {
			try {
				s_logger.info("Closing port...");
				m_commConnection.close();
				s_logger.info("Closed port");
			} catch (IOException e) {
				s_logger.error("Cannot close port", e);
			}
			m_commConnection = null;
		}
	}

	private void closePort() {
		cleanupPort();
	}

	private void doSerial() {
		if (m_commIs != null) {
			try {
				int c = -1;
				StringBuilder sb = new StringBuilder();
				while (m_commIs != null) {
					if (m_commIs.available() != 0) {
						c = m_commIs.read();
					} else {
						try {
							Thread.sleep(100);
							s_logger.debug(APP_ID + "no serial input ... sleeping");
							continue;
						} catch (InterruptedException e) {
							return;
						}
					}

					if (c == 13) {
						s_logger.debug(APP_ID + "serial input: " + sb.toString());
						if (parseSerial(sb.toString())) {
							// MQTT
						}
						sb = new StringBuilder();
					} else if (c != 10) {
						sb.append((char) c);
					}
				}
			} catch (IOException e) {
				s_logger.error(APP_ID + "Cannot read port", e);
			} finally {
				try {
					m_commIs.close();
				} catch (IOException e) {
					s_logger.error(APP_ID + "Cannot close buffered reader", e);
				}
			}
		}
	}

	private boolean parseSerial(String buf) {
		// [DHT11] Temperature: 27C / Humidity: 14%
		// \[[A-Z0-9]+\]\s+Temperature:\s+[\d]+C\s+\/\s+Humidity:\s+[\d]+%
		String regex = "\\[[A-Z0-9]+\\]\\s+Temperature:\\s+(\\d+)C\\s+\\/\\s+Humidity:\\s+(\\d+)%";
		Pattern r = Pattern.compile(regex);
		Matcher m = r.matcher(buf);
		if (m.find()) {
			temperature = Integer.parseInt(m.group(1));
			humidity = Integer.parseInt(m.group(2));
			return true;
		} else {
			return false;
		}
	}

	private void doPublish() {
		// fetch the publishing configuration from the publishing properties
		String topic = (String) this.m_properties.get(PUBLISH_TOPIC_PROP_NAME);
		Integer qos = (Integer) this.m_properties.get(PUBLISH_QOS_PROP_NAME);
		Boolean retain = (Boolean) this.m_properties.get(PUBLISH_RETAIN_PROP_NAME);
		// Allocate a new payload
		KuraPayload payload = new KuraPayload();
		// Timestamp the message
		payload.setTimestamp(new Date());
		// Add the temperature as a metric to the payload
		payload.addMetric("temperature", this.temperature);
		payload.addMetric("Humidity", this.humidity);
		// Publish the message
		try {
			this.m_cloudClient.publish(topic, payload, qos, retain);
		} catch (Exception e) {
			s_logger.error(APP_ID + "Cannot publish topic: " + topic, e);
		}
	}

}
