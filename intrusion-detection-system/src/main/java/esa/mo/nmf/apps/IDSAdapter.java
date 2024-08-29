/* ----------------------------------------------------------------------------
 * Copyright (C) 2021      European Space Agency
 *                         European Space Operations Centre
 *                         Darmstadt
 *                         Germany
 * ----------------------------------------------------------------------------
 * System                : ESA NanoSat MO Framework
 * ----------------------------------------------------------------------------
 * Licensed under European Space Agency Public License (ESA-PL) Weak Copyleft – v2.4
 * You may not use this file except in compliance with the License.
 *
 * Except as expressly set forth in this License, the Software is provided to
 * You on an "as is" basis and without warranties of any kind, including without
 * limitation merchantability, fitness for a particular purpose, absence of
 * defects or errors, accuracy or non-infringement of intellectual property rights.
 * 
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 * ----------------------------------------------------------------------------
 */
package esa.mo.nmf.apps;

import esa.mo.helpertools.connections.ConnectionConsumer;
import esa.mo.helpertools.misc.TaskScheduler;
import esa.mo.helpertools.helpers.HelperTime;
import esa.mo.nmf.MonitorAndControlNMFAdapter;
import esa.mo.nmf.NMFException;
import esa.mo.nmf.spacemoadapter.SpaceMOApdapterImpl;
import esa.mo.nmf.CloseAppListener;
import esa.mo.nmf.commonmoadapter.SimpleDataReceivedListener;
import esa.mo.nmf.nanosatmoconnector.NanoSatMOConnectorImpl;
import esa.mo.com.impl.util.EventCOMObject;
import esa.mo.com.impl.util.EventReceivedListener;
import java.io.IOException;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ccsds.moims.mo.mal.MALException;
import org.ccsds.moims.mo.mal.MALInteractionException;
import org.ccsds.moims.mo.mal.structures.Duration;
import org.ccsds.moims.mo.mal.structures.Identifier;
import org.ccsds.moims.mo.mal.structures.IdentifierList;
import org.ccsds.moims.mo.mal.structures.IntegerList;
import org.ccsds.moims.mo.mal.structures.UpdateHeaderList;
import org.ccsds.moims.mo.mal.structures.Union;
import org.ccsds.moims.mo.mal.structures.Time;
import org.ccsds.moims.mo.mal.transport.MALMessageHeader;
import org.ccsds.moims.mo.platform.autonomousadcs.consumer.AutonomousADCSAdapter;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.ActuatorsTelemetry;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeMode;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeModeBDot;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeModeNadirPointing;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeModeSingleSpinning;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeModeSunPointing;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeModeTargetTracking;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeTelemetry;
import org.ccsds.moims.mo.platform.autonomousadcs.structures.Quaternion;
import org.ccsds.moims.mo.platform.structures.VectorF3D;
import org.ccsds.moims.mo.platform.gps.body.GetLastKnownPositionResponse;
import org.ccsds.moims.mo.platform.gps.consumer.GPSAdapter;
import org.ccsds.moims.mo.mc.structures.AttributeValue;
import org.ccsds.moims.mo.mc.structures.AttributeValueList;
import org.ccsds.moims.mo.com.activitytracking.structures.ActivityExecution;
import com.opencsv.CSVWriter;
import java.util.Arrays;
import org.ccsds.moims.mo.mal.structures.UInteger;


/**
 * The adapter for the NMF App
 * This NMF App is used to collect relevant monitoring data, and perform anomaly detection on it.
 * The monitoring data originates from various sources: the ADCS Service, the GPS Service,
 * the Parameter Service, and the Event Services of both the supervisor and the other running Space App.
 * @author Nora Silven
 */
public class IDSAdapter extends MonitorAndControlNMFAdapter {

    // Utility variables.
    private static final Logger LOGGER = Logger.getLogger(IDSAdapter.class.getName());
    private static final Integer PERIOD = 10;
    private static final Duration ATTITUDE_MONITORING_INTERVAL = new Duration(10.0);
    private final TaskScheduler periodicMonitorTimer = new TaskScheduler(1);
    
    // App connector, connector to the Supervisor, and connector to the Camera Acquisitor System Space App.
    private NanoSatMOConnectorImpl connector;
    private SpaceMOApdapterImpl supervisorSMA;
    private SpaceMOApdapterImpl spaceAppSMA;

    // Names of Supervisor parameters that will be monitored.
    private static final String PARAMETER_NAMES = "SEPTM013,SEPTM011,SEPTM010,SEPTM003,SEPTM020";
    private List<String> parametersNames = new ArrayList<>();
    
    // Attitude and actuator telemetry parameters.
    private String SUNVECTOR_X = "";
    private String SUNVECTOR_Y = "";
    private String SUNVECTOR_Z = "";
    private String MAGFIELD_X = "";
    private String MAGFIELD_Y = "";
    private String MAGFIELD_Z = "";
    private String ANGVEL_X = "";
    private String ANGVEL_Y = "";
    private String ANGVEL_Z = "";
    private String ATTITUDE_A = "";
    private String ATTITUDE_B = "";
    private String ATTITUDE_C = "";
    private String ATTITUDE_D = "";
    private String DIPOLEMOM_X = "";
    private String DIPOLEMOM_Y = "";
    private String DIPOLEMOM_Z = "";
    private String ATTITUDE_MODE = "";
    private String REM_DURATION = "";
    
    // GPS sensor parameters.
    private String LATTITUDE = "";
    private String LONGITUDE = "";
    private String ALTITUDE = "";
    private String N_SATS = "";
    
    // Supervisor parameters.
    private String CPU_USE_IDLE = "";
    private String CPU_USE_SYS = "";
    private String CPU_USE_USR = "";
    private String MEMORY_USED = "";
    private String DISK_AVAIL_PERC = "";
    
    // Supervisor event related parameters.
    private String SUP_COM_EVENTS = "0";
    private String SUP_EXEC_EVENTS = "0";
    
    // Camera Aquisitor System event related parameters.
    private String CAS_COM_EVENTS = "0";
    private String CAS_EXEC_EVENTS = "0";
        
    // Writers needed to write the moniroting data and classification results to CSV.
    private CSVWriter writer;
    private FileWriter output_file;
    private CSVWriter label_writer;
    private FileWriter label_file;
    
    // The Loda anomaly detector, number of observed samples, and the initial window of samples.
    private Loda loda;
    private int sampleCount;
    private double[][] initSamples;
    private String[] initTimes;
    private int windowSize = 256;
    private String[] features = new String[]{"#SUPERVISOR COM EVENTS", "#SUPERVISOR EXEC EVENTS",
                                             "#CAS COM EVENTS", "#CAS EXEC EVENTS"};
    
    /**
     * Initialize the CSV writer, and write the first header line to the CSV file.
     */
    public IDSAdapter() {
        this.sampleCount = 0;
        this.initSamples = new double[this.windowSize][];
        this.initTimes = new String[this.windowSize];
        
        // Write headers of output and label files.
        try {
            this.output_file = new FileWriter("./output.csv");
            this.writer = new CSVWriter(this.output_file);
            this.label_file = new FileWriter("./labels.csv");
            this.label_writer = new CSVWriter(this.label_file);
            
            String[] header = {"timestamp", "sunVector_X", "sunVector_Y", "sunVector_Z",
                               "magneticField_X", "magneticField_Y", "magneticField_Z",
                               "angularVelocity_X", "angularVelocity_Y", "angularVelocity_Z",
                               "attitude_A", "attitude_B", "attitude_C", "attitude_D",
                               "mtqDipoleMoment_X", "mtqDipoleMoment_Y", "mtqDipoleMoment_Z",
                               "attitudeMode", "remainingDuration", "lattitude", "longtitude",
                               "altitude", "nSats", "cpuUseIdle", "cpuUseSys", "cpuUseUsr",
                               "memUsed", "diskAvailablePerc", "supComEvents", "supExecEvents",
                               "casComEvents", "casExecEvents"};
            
            this.writer.writeNext(header);
            this.label_writer.writeNext(new String[] {"timestamp", "class", "score", "featureScores"});
            this.writer.close();
            this.label_writer.close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Exception writing header to CSV", ex);
        }
    }
    
    
    /**
     * Set the NMF Connector and its closing method for this NMF App.
     * @param connector the connection that allows the app to be a provider.
     */
    public void setConnector(NanoSatMOConnectorImpl connector) {
        this.connector = connector;

        // Define application behavior when closed
        this.connector.setCloseAppListener(new CloseAppListener() {
            @Override
            public Boolean onClose() {
                return IDSAdapter.this.onClose();
            }
        });
    }

    
    /**
     * Set the Supervisor provider from which parameters and events will be consumed by this NMF App.
     * @param supervisorSMA the supervisor provider
     */
    public void setSupervisorSMA(SpaceMOApdapterImpl supervisorSMA) {
        this.supervisorSMA = supervisorSMA;
    }
    
    
    /**
     * Set the Camera Acquisitor System provider from which events will by consumer by this NMF App.
     * @param spaceAppSMA the camera acquisitor system provider
     */
    public void setSpaceAppSMA(SpaceMOApdapterImpl spaceAppSMA) {
        this.spaceAppSMA = spaceAppSMA;
    }
    
    
    /**
     * Start periodically collecting monitoring data, performing anomaly detection on the data, and
     * responding to any detected intrusions.
     */
    public void startMonitoring() {
        startAttitudeMonitoring();
        retrieveGPSParameters();
        startParameterMonitoring();
        startEventMonitoring();
                
        this.periodicMonitorTimer.scheduleTask(new Thread(() -> {
            retrieveGPSParameters();
                        
            if (!(SUNVECTOR_X.equals("")) && !(CPU_USE_IDLE.equals(""))) {
                                
                try {
                    output_file = new FileWriter("./output.csv", true);
                    writer = new CSVWriter(output_file);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Exception when setting up CSV writer.", ex);
                }
                
                String timeStamp = "";
            
                // Get the current time of collecting the current sample.
                try {
                    Time timestamp = connector.getPlatformServices().getClockService().getTime();
                    timeStamp = HelperTime.time2readableString(timestamp);
                } catch (IOException | NMFException | MALInteractionException | MALException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
                     
                // Create the sample of the only relevant features from simulated data.
                double[] sample = {Integer.parseInt(SUP_COM_EVENTS), Integer.parseInt(SUP_EXEC_EVENTS), 
                                   Integer.parseInt(CAS_COM_EVENTS), Integer.parseInt(CAS_EXEC_EVENTS)};
                
                String[][] result = this.handleSample(sample, timeStamp);
                          
                // Write the classification results to CSV.
                if (result != null) {
                    try {
                        this.label_file = new FileWriter("./labels.csv", true);
                        this.label_writer = new CSVWriter(this.label_file);
                        
                        for (String[] r : result) {
                            this.label_writer.writeNext(r, false);
                        }
                        
                        this.label_writer.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "Exception when setting up CSV writer.", ex);
                    }
                }
                
                String[] featureLine = {timeStamp, SUNVECTOR_X, SUNVECTOR_Y, SUNVECTOR_Z,
                                        MAGFIELD_X, MAGFIELD_Y, MAGFIELD_Z,
                                        ANGVEL_X, ANGVEL_Y, ANGVEL_Z,
                                        ATTITUDE_A, ATTITUDE_B, ATTITUDE_C, ATTITUDE_D,
                                        DIPOLEMOM_X, DIPOLEMOM_Y, DIPOLEMOM_Z,
                                        ATTITUDE_MODE, REM_DURATION, LATTITUDE,
                                        LONGITUDE, ALTITUDE, N_SATS, CPU_USE_IDLE,
                                        CPU_USE_SYS, CPU_USE_USR, MEMORY_USED,
                                        DISK_AVAIL_PERC, SUP_COM_EVENTS, SUP_EXEC_EVENTS,
                                        CAS_COM_EVENTS, CAS_EXEC_EVENTS};

                // Write all data features to CSV.
                try {
                    writer.writeNext(featureLine, false);
                    writer.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Exception while closing writer", ex);
                }
            
                // Reset events features.
                SUP_COM_EVENTS = "0";
                SUP_EXEC_EVENTS = "0";
                CAS_COM_EVENTS = "0";
                CAS_EXEC_EVENTS = "0";
            }
        }), 0, PERIOD, TimeUnit.SECONDS, true);
    }
    
    
    /**
     * Start collecting data regarding Supervisor and Camera Acquisitor System events.
     */
    public void startEventMonitoring() {
        supervisorSMA.getCOMServices().getEventService().addEventReceivedListener(ConnectionConsumer
                .subscriptionWildcard(), new EventReceivedAdapter());
        spaceAppSMA.getCOMServices().getEventService().addEventReceivedListener(ConnectionConsumer
                .subscriptionWildcard(), new EventReceivedAdapter());
    }
    
    
    /**
     * In this class, incoming events are handled.
     */
    public class EventReceivedAdapter extends EventReceivedListener {

        /**
         * Update the event-related feature variables according to the incoming event.
         * @param eventCOMObject the incoming event.
         */
        @Override
        public void onDataReceived(EventCOMObject eventCOMObject) {
            String domain = eventCOMObject.getDomain().get(2).toString();

            if (eventCOMObject.getBody() instanceof UInteger) {
                if (domain.equals("nanosat-mo-supervisor")) {
                    Integer current = Integer.valueOf(SUP_COM_EVENTS);
                    current += 1;
                    SUP_COM_EVENTS = String.valueOf(current);
                }
                
                if (domain.equals("camera-acquisitor-system")) {
                    Integer current = Integer.valueOf(CAS_COM_EVENTS);
                    current += 1;
                    CAS_COM_EVENTS = String.valueOf(current);
                }
            }
            
            if (eventCOMObject.getBody() instanceof ActivityExecution) {
                if (domain.equals("nanosat-mo-supervisor")) {
                    Integer current = Integer.valueOf(SUP_EXEC_EVENTS);
                    current += 1;
                    SUP_EXEC_EVENTS = String.valueOf(current);
                } else if (domain.equals("camera-acquisitor-system")) {
                    Integer current = Integer.valueOf(CAS_EXEC_EVENTS);
                    current += 1;
                    CAS_EXEC_EVENTS = String.valueOf(current);
                }
            }
        }
        
    }

    
    /**
     * Start monitoring supervisor parameters.
     */
    public void startParameterMonitoring() {
        LOGGER.log(Level.SEVERE, "Registering for the following parameters: " + PARAMETER_NAMES);
        parametersNames.clear();
        for (String paramName : PARAMETER_NAMES.split(",")) {
            parametersNames.add(paramName);
        }

        // Toggle the parameters generation in supervisor
        try {
            supervisorSMA.toggleParametersGeneration(parametersNames, true);
        } catch (NMFException e0) {
            LOGGER.log(Level.SEVERE, "Error toggling supervisor parameters generation", e0);
        }

        // Receive and handle the parameter values from supervisor
        SimpleDataReceivedListener parameterListener = new SimpleDataReceivedListener() {
            
            /**
             * Update the parameter-related feature variables according to the incoming parameter value.
             */
            @Override
            public void onDataReceived(String parameterName, Serializable data) {
                if (data == null) {
                    LOGGER.log(Level.WARNING, String.format("Received null value for parameter %s", parameterName));
                    return;
                }

                String dataS = data.toString();
                
                if (parameterName.equals("SEPTM013")) {
                    CPU_USE_IDLE = dataS;
                } else if (parameterName.equals("SEPTM011")) {
                    CPU_USE_SYS = dataS;
                } else if (parameterName.equals("SEPTM010")) {
                    CPU_USE_USR = dataS;
                } else if (parameterName.equals("SEPTM003")) {
                    MEMORY_USED = dataS;
                } else if (parameterName.equals("SEPTM020")) {
                    DISK_AVAIL_PERC = dataS;
                }
                
            }
        };

        supervisorSMA.addDataReceivedListener(parameterListener);
        LOGGER.log(Level.INFO, "Started fetching parameters from supervisor");
    }

    
    /**
     * Helper function to convert AttitudeMode to String.
     * @param attitude the active attitude mode
     * @return a string displaying the active attitude mode
     */
    private String attitudeModeToString(AttitudeMode attitude) {
        String mode;
        if (attitude == null) {
            mode = "IDLE";
        } else if (attitude instanceof AttitudeModeBDot) {
            mode = "BDOT";
        } else if (attitude instanceof AttitudeModeSunPointing) {
            mode = "SUNPOINTING";
        } else if (attitude instanceof AttitudeModeSingleSpinning) {
            mode = "SINGLESPINNING";
        } else if (attitude instanceof AttitudeModeTargetTracking) {
            mode = "TARGETTRACKING";
        } else if (attitude instanceof AttitudeModeNadirPointing) {
            mode = "NADIRPOINTING";
        } else {
            throw new IllegalArgumentException("Unrecognized attitude mode type!");
        }
        return mode;
    }

    
    /**
     * Start monitoring attitude and actuator telemetry.
     */
    public void startAttitudeMonitoring() {
        try {
            connector.getPlatformServices().getAutonomousADCSService().monitorAttitudeRegister(ConnectionConsumer
                .subscriptionWildcard(), new DataReceivedAdapter());
            connector.getPlatformServices().getAutonomousADCSService().enableMonitoring(true, ATTITUDE_MONITORING_INTERVAL);
        } catch (IOException | MALInteractionException | MALException | NMFException ex) {
            LOGGER.log(Level.SEVERE, "Error when setting up attitude monitoring.", ex);
        }
    }

    
    /**
     * This class handles the incoming attitude and actuator telemetry data.
     */
    public class DataReceivedAdapter extends AutonomousADCSAdapter {

        /**
         * Update the attitude-related feature variables according to the imcoming telemetry.
         * @param msgHeader information about the communication through which the telemetry was sent
         * @param lIdentifier information about the communication through which the telemetry was sent
         * @param lUpdateHeaderList information about the communication through which the telemetry was sent
         * @param attitudeTelemetryList attitude telemetry
         * @param actuatorsTelemetryList actuator telemetry
         * @param controlDurationList remaining duration of active attitude mode
         * @param attitudeModeList active attitude mode
         * @param qosp qosp?
         */
        @Override
        public void monitorAttitudeNotifyReceived(final MALMessageHeader msgHeader, final Identifier lIdentifier,
            final UpdateHeaderList lUpdateHeaderList,
            org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeTelemetryList attitudeTelemetryList,
            org.ccsds.moims.mo.platform.autonomousadcs.structures.ActuatorsTelemetryList actuatorsTelemetryList,
            org.ccsds.moims.mo.mal.structures.DurationList controlDurationList,
            org.ccsds.moims.mo.platform.autonomousadcs.structures.AttitudeModeList attitudeModeList, final Map qosp) {
            
            // Extract all data from attitude telemetry.
            for (AttitudeTelemetry attitudeTm : attitudeTelemetryList) {
                VectorF3D sunVector = attitudeTm.getSunVector();
                VectorF3D magneticField = attitudeTm.getMagneticField();
                VectorF3D angularVelocity = attitudeTm.getAngularVelocity();
                Quaternion attitude = attitudeTm.getAttitude();

                SUNVECTOR_X = sunVector.getX().toString();
                SUNVECTOR_Y = sunVector.getY().toString();
                SUNVECTOR_Z = sunVector.getZ().toString();
                MAGFIELD_X = magneticField.getX().toString();
                MAGFIELD_Y = magneticField.getZ().toString();
                MAGFIELD_Z = magneticField.getZ().toString();
                ANGVEL_X = angularVelocity.getX().toString();
                ANGVEL_Y = angularVelocity.getY().toString();
                ANGVEL_Z = angularVelocity.getZ().toString();
                ATTITUDE_A = attitude.getA().toString();
                ATTITUDE_B = attitude.getB().toString();
                ATTITUDE_C = attitude.getC().toString();
                ATTITUDE_D = attitude.getD().toString();
            }
            
            // Extract all data from actuator telemetry.
            for (ActuatorsTelemetry actuatorsTm : actuatorsTelemetryList) {
                VectorF3D mtqDipoleMoment = actuatorsTm.getMtqDipoleMoment();

                DIPOLEMOM_X = mtqDipoleMoment.getX().toString();
                DIPOLEMOM_Y = mtqDipoleMoment.getY().toString();
                DIPOLEMOM_Z = mtqDipoleMoment.getZ().toString();
            }
            
            // Extract attitude mode.
            for (Object activeAttitudeMode : attitudeModeList) {
                ATTITUDE_MODE = attitudeModeToString((AttitudeMode) activeAttitudeMode);
            }
            
            // Extract remaining duration of attitude mode.
            for (Duration remainingDuration : controlDurationList) {
                if (remainingDuration != null) {
                    REM_DURATION = remainingDuration.toString();
                } else {
                    REM_DURATION = "0.0";
                }
            }
        }
    }
    
    
    /**
     * Retrieve GPS lattitude, longitude, altitude, and number of satellites in view.
     */
    public void retrieveGPSParameters() {
        try {
            GetLastKnownPositionResponse pos = connector.getPlatformServices().getGPSService()
                .getLastKnownPosition();
                    
            LATTITUDE = pos.getBodyElement0().getLatitude().toString();
            LONGITUDE = pos.getBodyElement0().getLongitude().toString();
            ALTITUDE = pos.getBodyElement0().getAltitude().toString();
        } catch (IOException | NMFException | MALInteractionException | MALException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        final Semaphore sem = new Semaphore(0);
        final IntegerList nOfSats = new IntegerList();

        // This class is used to count the satellites in view.
        class AdapterImpl extends GPSAdapter {

            @Override
            public void getSatellitesInfoResponseReceived(
                org.ccsds.moims.mo.mal.transport.MALMessageHeader msgHeader,
                org.ccsds.moims.mo.platform.gps.structures.SatelliteInfoList gpsSatellitesInfo,
                java.util.Map qosProperties) {
                nOfSats.add(gpsSatellitesInfo.size());
                sem.release();
            }
        }

        try {
            connector.getPlatformServices().getGPSService().getSatellitesInfo(new AdapterImpl());
        } catch (IOException | NMFException | MALInteractionException | MALException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        try {
            sem.acquire();
            N_SATS = nOfSats.get(0).toString();
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Perform anomaly detection on a sample, and send an alert if the sample is anomalous.
     * @param sample the data sample
     * @param timeStamp the time of collection
     * @return the classification result
     */
    public String[][] handleSample(double[] sample, String timeStamp) {
        // The first window of samples is stored to use for Loda's initialization.
        if (this.sampleCount < this.windowSize) {
            this.initSamples[this.sampleCount] = sample;
            this.initTimes[this.sampleCount] = timeStamp;
            this.sampleCount++;
            
            // Initialize loda with the first sample window and classify these first samples.
            if ((this.sampleCount == this.windowSize) && (this.loda == null)) {
                int r = new Random().nextInt();
                loda = new Loda(140, 0.1f, r, this.initSamples);
                String[][] result = new String[this.windowSize][];

                int i = 0;
                for (double[] s : this.initSamples) {
                    
                    int p = this.loda.predict(s);
                    this.loda = this.loda.update(s);

                    // If the sample is anomalous, send an alert to the ground.
                    if (p == -1) {
                        double xScore = this.loda.scoreSample(s);
                        double[] fScores = this.loda.scoreFeatures(s);
                        String ranks = "Feature Scores: ";
                        String samp = "Data: ";
                        
                        // Create a string of the feature scores.
                        for (int f = 0; f < this.features.length; f++)
                            ranks += String.format("%s : %.3f\n", this.features[f], fScores[f]);
                        
                        // Create a string of the sample.
                        for (int f = 0; f < this.features.length; f++)
                            samp += String.format("%s : %.0f\n", this.features[f], s[f]);

                        AttributeValueList atts = new AttributeValueList();
                        AttributeValue time = new AttributeValue(new Union(this.initTimes[i]));
                        AttributeValue data = new AttributeValue(new Union(samp));
                        AttributeValue score = new AttributeValue(new Union(xScore));
                        AttributeValue featureScores = new AttributeValue(new Union(ranks));
                        
                        atts.add(time);
                        atts.add(data);
                        atts.add(score);
                        atts.add(featureScores);
                        result[i] = new String[]{this.initTimes[i], "1", String.valueOf(xScore),
                                                 Arrays.toString(fScores)};

                        try {
                            this.connector.publishAlertEvent("IntrusionDetectionAlert", atts);
                        } catch (NMFException ex) {
                            LOGGER.log(Level.SEVERE, "Error while publishing alert.", ex);
                        }
                    } else if (p == 1) {
                        double xScore = this.loda.scoreSample(s);
                        result[i] = new String[]{this.initTimes[i], "0", String.valueOf(xScore), "[]"};
                    }
                    
                    i++;
                }

                return result;
            }
        }
        
        // If loda has been initialized, handle the sample normally.
        if (this.loda != null) {
            String[][] result = new String[1][];
            
            int p = this.loda.predict(sample);
            this.loda = this.loda.update(sample);
            
            // If the sample is anomalous, send an alert to the ground.
            if (p == -1) {
                double xScore = this.loda.scoreSample(sample);
                double[] fScores = this.loda.scoreFeatures(sample);
                String ranks = "Feature Scores: ";
                String samp = "Data: ";
                
                // Create a string of the feature scores.
                for (int f = 0; f < this.features.length; f++)
                    ranks += String.format("%s : %.3f\n", this.features[f], fScores[f]);
                
                // Create a string of the sample data.
                for (int f = 0; f < this.features.length; f++)
                    samp += String.format("%s : %.0f\n", this.features[f], sample[f]);

                AttributeValueList atts = new AttributeValueList();
                AttributeValue time = new AttributeValue(new Union(timeStamp));
                AttributeValue data = new AttributeValue(new Union(samp));
                AttributeValue score = new AttributeValue(new Union(xScore));
                AttributeValue featureScores = new AttributeValue(new Union(ranks));

                atts.add(time);
                atts.add(data);
                atts.add(score);
                atts.add(featureScores);
                result[0] = new String[]{timeStamp, "1", String.valueOf(xScore),
                                         Arrays.toString(fScores)};
                
                try {
                    this.connector.publishAlertEvent("IntrusionDetectionAlert", atts);
                } catch (NMFException ex) {
                    LOGGER.log(Level.SEVERE, "Error while publishing alert.", ex);
                }
            } else if (p == 1) {
                double xScore = this.loda.scoreSample(sample);
                result[0] = new String[]{timeStamp, "0", String.valueOf(xScore), "[]"};
            }
                
            return result;
        }
        
        return null;
    }
    
    /**
     * Closes all connections.
     * @return success status
     */
    public Boolean onClose() {
        boolean success = true;
        // Stop fetching data in supervisor
        try {
            supervisorSMA.toggleParametersGeneration(parametersNames, false);
        } catch (NMFException e0) {
            success = false;
            LOGGER.log(Level.SEVERE, "Error toggling supervisor parameters generation", e0);
        }

        // Close supervisor consumer connections
        supervisorSMA.closeConnections();
        spaceAppSMA.closeConnections();

        // LOGGER.log(Level.INFO, "Closed application successfully: " + success);
        return success;
    }
}
