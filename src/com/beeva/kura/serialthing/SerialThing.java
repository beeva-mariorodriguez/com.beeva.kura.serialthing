package com.beeva.kura.serialthing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.eclipse.kura.comm.CommConnection;
import org.eclipse.kura.comm.CommURI;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.io.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SerialThing implements ConfigurableComponent {

    private static final Logger s_logger = LoggerFactory.getLogger(SerialThing.class);
    private static final String APP_ID = "com.beeva.kura.serialthing.SerialThing";

    private static final String SERIAL_DEVICE_PROP_NAME = "serial.device";
    private static final String SERIAL_BAUDRATE_PROP_NAME = "serial.baudrate";
    private static final String SERIAL_DATA_BITS_PROP_NAME = "serial.data-bits";
    private static final String SERIAL_PARITY_PROP_NAME = "serial.parity";
    private static final String SERIAL_STOP_BITS_PROP_NAME = "serial.stop-bits";

    private ConnectionFactory m_connectionFactory;
    private CommConnection m_commConnection;
    private InputStream m_commIs;
    private OutputStream m_commOs;

    private ScheduledThreadPoolExecutor m_worker;
    private Future<?> m_handle;

    private Map<String, Object> m_properties;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

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
        m_worker = new ScheduledThreadPoolExecutor(1);
        m_properties = new HashMap<String, Object>();
        doUpdate(properties);
        s_logger.info("Activating " + APP_ID + "Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        s_logger.info("Deactivating " + APP_ID);

        // shutting down the worker and cleaning up the properties
        m_handle.cancel(true);
        m_worker.shutdownNow();

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

            // cancel a current worker handle if one if active
            if (m_handle != null) {
                m_handle.cancel(true);
            }

            // close the serial port so it can be reconfigured
            closePort();

            // store the properties
            m_properties.clear();
            m_properties.putAll(properties);

            // reopen the port with the new configuration
            openPort();

            // start the worker thread
            m_handle = m_worker.submit(new Runnable() {

                @Override
                public void run() {
                    doSerial();
                }
            });
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
        if (sParity.equals("none")) {
            parity = CommURI.PARITY_NONE;
        } else if (sParity.equals("odd")) {
            parity = CommURI.PARITY_ODD;
        } else if (sParity.equals("even")) {
            parity = CommURI.PARITY_EVEN;
        }

        String uri = new CommURI.Builder(port).withBaudRate(baudRate).withDataBits(dataBits).withStopBits(stopBits)
                .withParity(parity).withTimeout(1000).build().toString();
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
                            continue;
                        } catch (InterruptedException e) {
                            return;
                        }
                    }

                    // on reception of CR, publish the received sentence
                    if (c == 13) {
                        s_logger.debug("Received serial input, echoing to output: " + sb.toString());
                        sb.append("\r\n");
                        String dataRead = sb.toString();

                        // echo the data to the output stream
                        m_commOs.write(dataRead.getBytes());

                        // reset the buffer
                        sb = new StringBuilder();
                    } else if (c != 10) {
                        sb.append((char) c);
                    }
                }
            } catch (IOException e) {
                s_logger.error("Cannot read port", e);
            } finally {
                try {
                    m_commIs.close();
                } catch (IOException e) {
                    s_logger.error("Cannot close buffered reader", e);
                }
            }
        }
    }
}