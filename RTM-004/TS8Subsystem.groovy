import org.lsst.ccs.drivers.reb.sim.ClientFactorySimulation
import org.lsst.ccs.subsystem.rafts.RaftsMain;
import org.lsst.ccs.subsystem.rafts.REBDevice;
import org.lsst.ccs.subsystem.rafts.DacControl;
import org.lsst.ccs.subsystem.rafts.AspicControl;
import org.lsst.ccs.subsystem.rafts.BiasControl;
import org.lsst.ccs.subsystem.rafts.TempControl;
import org.lsst.ccs.subsystem.rafts.alerts.RaftAlert;
import org.lsst.ccs.monitor.Alarm;
import org.lsst.ccs.monitor.Channel;
import org.lsst.ccs.utilities.ccd.*;
import org.lsst.ccs.utilities.image.*;
import org.lsst.ccs.subsystem.ts8.TS8Subsystem;
import org.lsst.ccs.subsystem.ts8.TS8AlertHandler;
import org.lsst.ccs.subsystem.ts8.sim.*;
import org.lsst.ccs.subsystem.ts8.sim.TS8ClientFactorySimulation
import org.lsst.ccs.description.groovy.CCSBuilder
import org.lsst.ccs.monitor.Page
import org.lsst.ccs.drivers.reb.ClientFactory
import org.lsst.ccs.drivers.reb.SlowAdcs
import org.lsst.ccs.subsystem.rafts.GlobalVisualizationClient
import org.lsst.ccs.bootstrap.BootstrapResourceUtils;

CCDType type = new ITLCCDType();

raftGeometry = new Raft(type);
reb0 = Reb.createReb(type);
raftGeometry.addChildGeometry(reb0, 0, 0);
reb1 = Reb.createReb(type);
raftGeometry.addChildGeometry(reb1, 1, 0);
reb2 = Reb.createReb(type);
raftGeometry.addChildGeometry(reb2, 2, 0);

Properties props = BootstrapResourceUtils.getBootstrapSystemProperties()
def runMode = props.getProperty("org.lsst.ccs.run.mode","normal");
System.out.println("Building TS8 subsystem in run mode: "+runMode);

ClientFactory mainClientFactory = runMode.equals("simulation") ? new ClientFactorySimulation() : new ClientFactory();

taskConfig = ["monitor-update/taskPeriodMillis":1000,"monitor-publish/taskPeriodMillis":10000,
            "agentStatusAggregatorService/patternConfigList":[
                "[pattern:.*,predicate:[agentName:${System.getProperty('org.lsst.ccs.subsystem.teststand', 'ts')}]]",
                "[pattern:.*,predicate:[agentName:${System.getProperty('org.lsst.ccs.subsystem.rebps', 'ccs-rebps')}]]",
                "[pattern:.*,predicate:[agentName:${System.getProperty('org.lsst.ccs.subsystem.ts8', 'ts8')}]]",
                "[pattern:.*,predicate:[agentName:${System.getProperty('org.lsst.ccs.subsystem.ts8-bench', 'ts8-bench')}]]"
                ]]

int raftId = Integer.valueOf(props.getProperty("org.lsst.ccs.rafts.id", "22"))
def partition = props.getProperty("org.lsst.ccs.rafts.partition","TS6")

CCSBuilder builder = ["ts8-otm1"]

builder.
    "main" (TS8Subsystem, geometry:raftGeometry, clientFactory:mainClientFactory, nodeTags:taskConfig, ccdType:"itl" ) { 
    
//        GlobalVisualizationClient (GlobalVisualizationClient, clientHost:"lsst-vs01.slac.stanford.edu", clientPort:9999)
    
	TempCtrl   (TempControl, gain: 0.1, timeConst: 120.0, smoothTime: 20.0,
                maxOutput: 5.6, awGain: 4.0, basePower: 0.0, tolerance: 0.05,
                updateTime: 30000, rebs: ["R00.Reb1"], tempChans: ["R00.Reb1.CCDTemp1"])
//                updateTime: 30000, rebs: ["R00.Reb0", "R00.Reb2"], tempChans: ["R00.Reb0.CCDTemp1", "R00.Reb1.CCDTemp1", "R00.Reb2.CCDTemp1"])

        TS8AlertHandler (TS8AlertHandler)
        
        RebAspicTemperatureLowLimit  (Alarm, description:"Alarm for Aspic low temperature limit", eventParm:RaftAlert.ASPICS_TEMPERATURE_TOO_LOW.ordinal())
        RebAspicTemperatureHighLimit  (Alarm, description:"Alarm for Aspic high temperature limit", eventParm:RaftAlert.ASPICS_TEMPERATURE_TOO_HIGH.ordinal())
        RebBoardTemperatureLowLimit  (Alarm, description:"Alarm for Reb board low temperature limit", eventParm:RaftAlert.REB_BOARD_TEMPERATURE_TOO_LOW.ordinal())
        RebBoardTemperatureHighLimit  (Alarm, description:"Alarm for Reb board high temperature limit", eventParm:RaftAlert.REB_BOARD_TEMPERATURE_TOO_HIGH.ordinal())

        for (Reb rebGeometry : raftGeometry.getChildrenList() ) {
            def rebCount = 4*raftId+rebGeometry.getParallelPosition();
            System.out.println("Using Reb Id "+rebCount);
            def reb = rebGeometry.getUniqueId();
            def dreb = "D$reb"
                    

            innerClientFactory = runMode.equals("simulation") ? new TS8ClientFactorySimulation(rebGeometry) : null;

        "$reb" (REBDevice, hdwType:"daq2", id:rebCount, ifcName:partition, ccdMask:7, clientFactory:innerClientFactory) {

            //"${reb}.DAC" (DacControl, raw: true, version: 2)   // Science raft REBs, raw DAC values
            "${reb}.DAC" (DacControl)    // All REBs, physical values

            for (int j = 0; j < 6; j++) {
                def apc = "ASPIC$j"
                //"${reb}.${apc}" (AspicControl, description: "", hwChan: j, gain: 0, rc: 0, clamp: 0, af1: 0, tm: 0)
                "${reb}.${apc}" (AspicControl, hwChan: j)
            }

            for (int j = 0; j < 3; j++) {
                //"${reb}.Bias$j" (BiasControl, hwChan: j, raw: true)   // All REBs. raw DAC values
                "${reb}.Bias$j" (BiasControl, hwChan: j)   // All REBs, physical values
            }
        }

        "Page$rebCount" (Page, id: rebCount, label: "${reb}")


        String title = "${reb} temperatures\\"
        for (int j = 1; j <= 10; j++) {
            if ( j == 3 || j == 6 ) {                
                    "${reb}.Temp$j" (Channel, description: "${title}Board temperature $j", units: "\u00b0C",
                        devcName: "${reb}", hwChan: j - 1, type: "TEMP", pageId: rebCount,
                        checkLo:"alarm", limitLo:10.0, dbandLo:5.0, alarmLo:"RebBoardTemperatureLowLimit",
                        checkHi:"alarm", limitHi:45, dbandHi:5.0, alarmHi:"RebBoardTemperatureHighLimit")
            } else {
                    "${reb}.Temp$j" (Channel, description: "${title}Board temperature $j", units: "\u00b0C",
                        devcName: "${reb}", hwChan: j - 1, type: "TEMP", pageId: rebCount,
                        checkLo:"flag", limitLo:10.0, dbandLo:5.0,
                        checkHi:"flag", limitHi:45, dbandHi:5.0)                
            }
            title = ""
        }

        "${reb}.Atemp0U" (Channel, description: "ASPIC 0 upper temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 0, type: "ATEMP", pageId: rebCount,
                          checkLo:"flag", limitLo:10.0, dbandLo:5.0/*, alarmLo:"RebAspicTemperatureLowLimit"*/,
                          checkHi:"flag", limitHi:45, dbandHi:5.0/*, alarmHi:"RebAspicTemperatureHighLimit"*/)

        "${reb}.Atemp0L" (Channel, description: "ASPIC 0 lower temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 1, type: "ATEMP", pageId: rebCount,
                          checkLo:"flag", limitLo:10.0, dbandLo:5.0/*, alarmLo:"RebAspicTemperatureLowLimit"*/,
                          checkHi:"flag", limitHi:45, dbandHi:5.0/*, alarmHi:"RebAspicTemperatureHighLimit"*/)

        "${reb}.Atemp1U" (Channel, description: "ASPIC 1 upper temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 2, type: "ATEMP", pageId: rebCount,
                          checkLo:"flag", limitLo:10.0, dbandLo:5.0/*, alarmLo:"RebAspicTemperatureLowLimit"*/,
                          checkHi:"flag", limitHi:45, dbandHi:5.0/*, alarmHi:"RebAspicTemperatureHighLimit"*/)

        "${reb}.Atemp1L" (Channel, description: "ASPIC 1 lower temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 3, type: "ATEMP", pageId: rebCount,
                          checkLo:"flag", limitLo:10.0, dbandLo:5.0/*, alarmLo:"RebAspicTemperatureLowLimit"*/,
                          checkHi:"flag", limitHi:45, dbandHi:5.0/*, alarmHi:"RebAspicTemperatureHighLimit"*/)

        "${reb}.Atemp2U" (Channel, description: "ASPIC 2 upper temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 4, type: "ATEMP", pageId: rebCount,
                          checkLo:"flag", limitLo:10.0, dbandLo:5.0/*, alarmLo:"RebAspicTemperatureLowLimit"*/,
                          checkHi:"flag", limitHi:45, dbandHi:5.0/*, alarmHi:"RebAspicTemperatureHighLimit"*/)

        "${reb}.Atemp2L" (Channel, description: "ASPIC 2 lower temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 5, type: "ATEMP", pageId: rebCount,
                          checkLo:"flag", limitLo:10.0, dbandLo:5.0/*, alarmLo:"RebAspicTemperatureLowLimit"*/,
                          checkHi:"flag", limitHi:45, dbandHi:5.0/*, alarmHi:"RebAspicTemperatureHighLimit"*/)
///*
        for (int j = 0; j < 3; j++) {
            "${reb}.CCDTemp$j" (Channel, description: "CCD $j temperature", units: "\u00b0C",
                                devcName: "${reb}", hwChan: j, type: "RTD", pageId: rebCount)
        }

        "${reb}.RTDTemp" (Channel, description: "RTD temperature", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 3, type: "RTD", pageId: rebCount)
//*/
        "${reb}.HtrV"    (Channel, description: "$reb heater\\Heater voltage", units: "Volts",
                      devcName: "${reb}", hwChan: REBDevice.CHAN_HTR_VOLTS, type: "HEATER", pageId: rebCount)

        "${reb}.HtrW"    (Channel, description: "Heater power", units: "Watts",
                      devcName: "${reb}", hwChan: REBDevice.CHAN_HTR_POWER, type: "HEATER", pageId: rebCount)

        "${reb}.DigV"    (Channel, description: "${reb} board power\\Digital PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 0, type: "POWER", pageId: rebCount)

        "${reb}.DigI"    (Channel, description: "Digital PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 1, type: "POWER", scale: 1000, pageId: rebCount)

        "${reb}.AnaV"    (Channel, description: "Analog PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 2, type: "POWER", pageId: rebCount)

        "${reb}.AnaI"    (Channel, description: "Analog PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 3, type: "POWER", scale: 1000, pageId: rebCount)

//        "${reb}.ClkV"    (Channel, description: "Clock PS voltage", units: "Volts",
//                          devcName: "${reb}", hwChan: 4, type: "POWER", pageId: rebCount)

//        "${reb}.ClkI"    (Channel, description: "Clock PS current", format: ".1f", units: "mA",
//                          devcName: "${reb}", hwChan: 5, type: "POWER", scale: 1000, pageId: rebCount)

        "${reb}.ClkHV"   (Channel, description: "Clock High PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 4, type: "POWER", pageId: rebCount)

        "${reb}.ClkHI"   (Channel, description: "Clock High PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 5, type: "POWER", scale: 1000, pageId: rebCount)

        "${reb}.ClkLV"   (Channel, description: "Clock Low PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 8, type: "POWER", pageId: rebCount)

        "${reb}.ClkLI"   (Channel, description: "Clock Low PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 9, type: "POWER", scale: 1000, pageId: rebCount)

        "${reb}.ODV"     (Channel, description: "OD PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 6, type: "POWER", pageId: rebCount)

        "${reb}.ODI"     (Channel, description: "OD PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 7, type: "POWER", scale: 1000, pageId: rebCount)

        "${reb}.Power"   (Channel, description: "Total power", format: ".2f", units: "Watts",
                          devcName: "${reb}", hwChan: REBDevice.CHAN_TOTAL_POWER, type: "POWER", pageId: rebCount)

        "${reb}.PClkU"   (Channel, description: "${reb} clock rails\\Parallel upper", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_CKP_U, type: "BIAS", pageId: rebCount)

        "${reb}.PClkL"   (Channel, description: "Parallel lower", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_CKP_L, type: "BIAS", pageId: rebCount)

        "${reb}.SClkU"   (Channel, description: "Serial upper", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_SCK_U, type: "BIAS", pageId: rebCount)

        "${reb}.SClkL"   (Channel, description: "Serial lower", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_SCK_L, type: "BIAS", pageId: rebCount)

        "${reb}.RGU"     (Channel, description: "Reset gate upper", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RG_U, type: "BIAS", pageId: rebCount)

        "${reb}.RGL"     (Channel, description: "Reset gate lower", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RG_L, type: "BIAS", pageId: rebCount)

        "${reb}.RefP12"  (Channel, description: "Positive 12 ref", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VP12, type: "BIAS", pageId: rebCount)

        "${reb}.RefN12"  (Channel, description: "Negative 12 ref", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VN12, type: "BIAS", pageId: rebCount)

        "${reb}.OD0V"    (Channel, description: "${reb} bias voltages\\OD 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OD_0, type: "BIAS", pageId: rebCount)

        "${reb}.OG0V"    (Channel, description: "OG 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OG_0, type: "BIAS", pageId: rebCount)

        "${reb}.RD0V"    (Channel, description: "RD 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RD_0, type: "BIAS", pageId: rebCount)

        "${reb}.GD0V"    (Channel, description: "GD 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_GD_0, type: "BIAS", pageId: rebCount)

        "${reb}.OD1V"    (Channel, description: "OD 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OD_1, type: "BIAS", pageId: rebCount)

        "${reb}.OG1V"    (Channel, description: "OG 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OG_1, type: "BIAS", pageId: rebCount)

        "${reb}.RD1V"    (Channel, description: "RD 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RD_1, type: "BIAS", pageId: rebCount)

        "${reb}.GD1V"    (Channel, description: "GD 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_GD_1, type: "BIAS", pageId: rebCount)

        "${reb}.OD2V"    (Channel, description: "OD 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OD_2, type: "BIAS", pageId: rebCount)

        "${reb}.OG2V"    (Channel, description: "OG 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OG_2, type: "BIAS", pageId: rebCount)

        "${reb}.RD2V"    (Channel, description: "RD 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RD_2, type: "BIAS", pageId: rebCount)

        "${reb}.GD2V"    (Channel, description: "GD 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_GD_2, type: "BIAS", pageId: rebCount)

        "${reb}.Ref05V"  (Channel, description: "5V ref 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF5_0, type: "BIAS", pageId: rebCount)

        "${reb}.Ref15V"  (Channel, description: "5V ref 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF5_1, type: "BIAS", pageId: rebCount)

        "${reb}.Ref25V"  (Channel, description: "5V ref 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF5_2, type: "BIAS", pageId: rebCount)

        "${reb}.Ref125V" (Channel, description: "2.5V ref 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF25_1, type: "BIAS", pageId: rebCount)

        title = "${reb} CCD currents\\"
        for (int js = 0; js < 3; js++) {
            for (int jd = 0; jd < 2; jd++) {
                String tb = jd == 0 ? "upper" : "lower"
                for (int jc = 0; jc < 8; jc++) {
                    int ch = jc + 8 * jd + 16 * js
                    "${reb}.CCDI${js}${jd}${jc}" \
                         (Channel, description: "${title}CCD ${js} ${tb} current ${jc}", format: ".3f", units: "mA",
                          devcName: "${reb}", hwChan: ch, type: "CURR", scale: 1000, pageId: rebCount)
                    title = ""
                }
            }
        }

    }



         
}
