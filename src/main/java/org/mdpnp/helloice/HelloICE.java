package org.mdpnp.helloice;

import com.rti.dds.domain.DomainParticipant;
import com.rti.dds.domain.DomainParticipantFactory;
import com.rti.dds.infrastructure.ConditionSeq;
import com.rti.dds.infrastructure.Duration_t;
import com.rti.dds.infrastructure.RETCODE_NO_DATA;
import com.rti.dds.infrastructure.ResourceLimitsQosPolicy;
import com.rti.dds.infrastructure.StatusKind;
import com.rti.dds.infrastructure.WaitSet;
import com.rti.dds.subscription.InstanceStateKind;
import com.rti.dds.subscription.SampleInfo;
import com.rti.dds.subscription.SampleInfoSeq;
import com.rti.dds.subscription.SampleStateKind;
import com.rti.dds.subscription.Subscriber;
import com.rti.dds.subscription.ViewStateKind;
import com.rti.dds.topic.Topic;

import org.mdpnp.rtiapi.data.QosProfiles;

public class HelloICE {
    public static void main(String[] args) {
        int domainId = 0;

        // domainId is the one command line argument
        if(args.length > 0) {
            domainId = Integer.parseInt(args[0]);
        }

        // Here we use 'default' Quality of Service settings where QoS settings are configured via the USER_QOS_PROFILES.xml
        // in the current working directory

        // A domain participant is the main access point into the DDS domain.  Endpoints are created within the domain participant
        DomainParticipant participant = DomainParticipantFactory.get_instance().create_participant(domainId, DomainParticipantFactory.PARTICIPANT_QOS_DEFAULT, null, StatusKind.STATUS_MASK_NONE);

        // Inform the participant about the sample array data type we would like to use in our endpoints
        ice.SampleArrayTypeSupport.register_type(participant, ice.SampleArrayTypeSupport.get_type_name());

        // Inform the participant about the numeric data type we would like to use in our endpoints
        ice.NumericTypeSupport.register_type(participant, ice.NumericTypeSupport.get_type_name());

        // A topic the mechanism by which reader and writer endpoints are matched.
        Topic sampleArrayTopic = participant.create_topic(ice.SampleArrayTopic.VALUE, ice.SampleArrayTypeSupport.get_type_name(), DomainParticipant.TOPIC_QOS_DEFAULT, null, StatusKind.STATUS_MASK_NONE);

        // A second topic if for Numeric data
        Topic numericTopic = participant.create_topic(ice.NumericTopic.VALUE, ice.NumericTypeSupport.get_type_name(), DomainParticipant.TOPIC_QOS_DEFAULT, null, StatusKind.STATUS_MASK_NONE);

        // Create a reader endpoint for samplearray data
        ice.SampleArrayDataReader saReader = (ice.SampleArrayDataReader) participant.create_datareader_with_profile(sampleArrayTopic, QosProfiles.ice_library, QosProfiles.waveform_data, null, StatusKind.STATUS_MASK_NONE);

        ice.NumericDataReader nReader = (ice.NumericDataReader) participant.create_datareader_with_profile(numericTopic, QosProfiles.ice_library, QosProfiles.numeric_data, null, StatusKind.STATUS_MASK_NONE);

        // A waitset allows us to wait for various status changes in various entities
        WaitSet ws = new WaitSet();

        // Here we configure the status condition to trigger when new data becomes available to the reader
        saReader.get_statuscondition().set_enabled_statuses(StatusKind.DATA_AVAILABLE_STATUS);

        nReader.get_statuscondition().set_enabled_statuses(StatusKind.DATA_AVAILABLE_STATUS);

        // And register that status condition with the waitset so we can monitor its triggering
        ws.attach_condition(saReader.get_statuscondition());

        ws.attach_condition(nReader.get_statuscondition());

        // will contain triggered conditions
        ConditionSeq cond_seq = new ConditionSeq();

        // we'll wait as long as necessary for data to become available
        Duration_t timeout = new Duration_t(Duration_t.DURATION_INFINITE_SEC, Duration_t.DURATION_INFINITE_NSEC);

        // Will contain the data samples we read from the reader
        ice.SampleArraySeq sa_data_seq = new ice.SampleArraySeq();

        ice.NumericSeq n_data_seq = new ice.NumericSeq();

        // Will contain the SampleInfo information about those data
        SampleInfoSeq info_seq = new SampleInfoSeq();

        // This loop will repeat until the process is terminated
        for(;;) {
            // Wait for a condition to be triggered
            ws.wait(cond_seq, timeout);
            // Check that our status condition was indeed triggered
            if(cond_seq.contains(saReader.get_statuscondition())) {
                // read the actual status changes
                int status_changes = saReader.get_status_changes();

                // Ensure that DATA_AVAILABLE is one of the statuses that changed in the DataReader.
                // Since this is the only enabled status (see above) this is here mainly for completeness
                if(0 != (status_changes & StatusKind.DATA_AVAILABLE_STATUS)) {
                    try {
                        // Read samples from the reader
                        saReader.read(sa_data_seq,info_seq, ResourceLimitsQosPolicy.LENGTH_UNLIMITED, SampleStateKind.NOT_READ_SAMPLE_STATE, ViewStateKind.ANY_VIEW_STATE, InstanceStateKind.ALIVE_INSTANCE_STATE);

                        // Iterator over the samples
                        for(int i = 0; i < info_seq.size(); i++) {
                            SampleInfo si = (SampleInfo) info_seq.get(i);
                            ice.SampleArray data = (ice.SampleArray) sa_data_seq.get(i);
                            // If the updated sample status contains fresh data that we can evaluate
                            if(si.valid_data) {
                                System.out.println(data);
                            }

                        }
                    } catch (RETCODE_NO_DATA noData) {
                        // No Data was available to the read call
                    } finally {
                        // the objects provided by "read" are owned by the reader and we must return them
                        // so the reader can control their lifecycle
                        saReader.return_loan(sa_data_seq, info_seq);
                    }
                }
            }
            if(cond_seq.contains(nReader.get_statuscondition())) {
                // read the actual status changes
                int status_changes = nReader.get_status_changes();

                // Ensure that DATA_AVAILABLE is one of the statuses that changed in the DataReader.
                // Since this is the only enabled status (see above) this is here mainly for completeness
                if(0 != (status_changes & StatusKind.DATA_AVAILABLE_STATUS)) {
                    try {
                        // Read samples from the reader
                        nReader.read(n_data_seq,info_seq, ResourceLimitsQosPolicy.LENGTH_UNLIMITED, SampleStateKind.NOT_READ_SAMPLE_STATE, ViewStateKind.ANY_VIEW_STATE, InstanceStateKind.ALIVE_INSTANCE_STATE);

                        // Iterator over the samples
                        for(int i = 0; i < info_seq.size(); i++) {
                            SampleInfo si = (SampleInfo) info_seq.get(i);
                            ice.Numeric data = (ice.Numeric) n_data_seq.get(i);
                            // If the updated sample status contains fresh data that we can evaluate
                            if(si.valid_data) {
                                if(data.metric_id.equals(rosetta.MDC_PULS_OXIM_SAT_O2.VALUE)) {
                                    // This is an O2 saturation from pulse oximetry
//                                    System.out.println("SpO2="+data.value);
                                } else if(data.metric_id.equals(rosetta.MDC_PULS_OXIM_PULS_RATE.VALUE)) {
                                    // This is a pulse rate from pulse oximetry
//                                  System.out.println("Pulse Rate="+data.value);
                                }
                                System.out.println(data);
                            }

                        }
                    } catch (RETCODE_NO_DATA noData) {
                        // No Data was available to the read call
                    } finally {
                        // the objects provided by "read" are owned by the reader and we must return them
                        // so the reader can control their lifecycle
                        nReader.return_loan(n_data_seq, info_seq);
                    }
                }
            }
        }
    }
}
