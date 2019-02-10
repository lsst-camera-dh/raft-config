import org.lsst.ccs.description.groovy.CCSBuilder
import org.lsst.ccs.subsystem.rafts.RaftsMain
import org.lsst.ccs.subsystem.rafts.REBDevice
import org.lsst.ccs.subsystem.rafts.DacControl
import org.lsst.ccs.subsystem.rafts.AspicControl
import org.lsst.ccs.subsystem.rafts.BiasControl
import org.lsst.ccs.subsystem.rafts.TempControl
import org.lsst.ccs.monitor.Channel
import org.lsst.ccs.monitor.Page
import org.lsst.ccs.drivers.reb.SlowAdcs
import org.lsst.ccs.drivers.reb.sim.ClientFactorySimulation

taskConfig = ["monitor-update/taskPeriodMillis":1000,"monitor-publish/taskPeriodMillis":10000]

CCSBuilder builder = ["ccs-raft5"]

def runMode = System.getProperty("org.lsst.ccs.run.mode", "normal")
factory = runMode.equals("simulation") ? new ClientFactorySimulation() : null

int nreb = Integer.valueOf(System.getProperty("org.lsst.ccs.rafts.nreb", "3"))

builder.main (RaftsMain, clientFactory:factory, nodeTags:taskConfig) {

    TempCtrl   (TempControl, gain: 1.0, timeConst: 1000.0, smoothTime: 120.0,
                maxOutput: 5.6, awGain: 4.0, basePower: 0.0, tolerance: 0.05,
                updateTime: 30000, rebs: ["REB0", "REB2"], tempChans: ["REB0.RTDTemp", "REB1.RTDTemp", "REB2.RTDTemp"])

    for (int i = 0; i < nreb; i++) {
        def reb = "REB$i"

        "$reb" (REBDevice, hdwType:"daq1", id:i, ifcName:"", ccdMask:7, clientFactory:factory) {

            //"${reb}.DAC" (DacControl, raw: true)   // All REBs, raw DAC values
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

        "Page$i" (Page, id: i, label: "${reb}")

        String title = "${reb} temperatures\\"
        for (int j = 1; j <= 10; j++) {
            "${reb}.Temp$j" (Channel, description: "${title}Board temperature $j", units: "\u00b0C",
                             devcName: "${reb}", hwChan: j - 1, type: "TEMP", pageId: i)
            title = ""
        }

        "${reb}.Atemp0U" (Channel, description: "ASPIC 0 upper temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 0, type: "ATEMP", pageId: i)

        "${reb}.Atemp0L" (Channel, description: "ASPIC 0 lower temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 1, type: "ATEMP", pageId: i)

        "${reb}.Atemp1U" (Channel, description: "ASPIC 1 upper temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 2, type: "ATEMP", pageId: i)

        "${reb}.Atemp1L" (Channel, description: "ASPIC 1 lower temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 3, type: "ATEMP", pageId: i)

        "${reb}.Atemp2U" (Channel, description: "ASPIC 2 upper temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 4, type: "ATEMP", pageId: i)

        "${reb}.Atemp2L" (Channel, description: "ASPIC 2 lower temp", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 5, type: "ATEMP", pageId: i)
///*
        for (int j = 0; j < 3; j++) {
            "${reb}.CCDTemp$j" (Channel, description: "CCD $j temperature", units: "\u00b0C",
                                devcName: "${reb}", hwChan: j, type: "RTD", pageId: i)
        }

        "${reb}.RTDTemp" (Channel, description: "RTD temperature", units: "\u00b0C",
                          devcName: "${reb}", hwChan: 3, type: "RTD", pageId: i)
//*/
        "${reb}.DigV"    (Channel, description: "${reb} board power\\Digital PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 0, type: "POWER", pageId: i)

        "${reb}.DigI"    (Channel, description: "Digital PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 1, type: "POWER", scale: 1000, pageId: i)

        "${reb}.DigP"    (Channel, description: "Digital PS power", format: ".2f", units: "Watts",
                          devcName: null, type: "PROD", subtype: "${reb}.DigV:${reb}.DigI", scale: 0.001, pageId: i)

        "${reb}.AnaV"    (Channel, description: "Analog PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 2, type: "POWER", pageId: i)

        "${reb}.AnaI"    (Channel, description: "Analog PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 3, type: "POWER", scale: 1000, pageId: i)

        "${reb}.AnaP"    (Channel, description: "Analog PS power", format: ".2f", units: "Watts",
                          devcName: null, type: "PROD", subtype: "${reb}.AnaV:${reb}.AnaI", scale: 0.001, pageId: i)

        "${reb}.ClkV"    (Channel, description: "Clock PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 4, type: "POWER", pageId: i)

        "${reb}.ClkI"    (Channel, description: "Clock PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 5, type: "POWER", scale: 1000, pageId: i)

        "${reb}.ClkP"    (Channel, description: "Clock PS power", format: ".2f", units: "Watts",
                          devcName: null, type: "PROD", subtype: "${reb}.ClkV:${reb}.ClkI", scale: 0.001, pageId: i)

        "${reb}.ODV"     (Channel, description: "OD PS voltage", units: "Volts",
                          devcName: "${reb}", hwChan: 6, type: "POWER", pageId: i)

        "${reb}.ODI"     (Channel, description: "OD PS current", format: ".1f", units: "mA",
                          devcName: "${reb}", hwChan: 7, type: "POWER", scale: 1000, pageId: i)

        "${reb}.ODP"     (Channel, description: "OD PS power", format: ".2f", units: "Watts",
                          devcName: null, type: "PROD", subtype: "${reb}.ODV:${reb}.ODI", scale: 0.001, pageId: i)

        "${reb}.Power"   (Channel, description: "Total power", format: ".2f", units: "Watts",
                          devcName: "$reb", hwChan: REBDevice.CHAN_TOTAL_POWER, type: "POWER", pageId: i)

        "${reb}.PClkU"   (Channel, description: "${reb} clock rails\\Parallel upper", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_CKP_U, type: "BIAS", pageId: i)

        "${reb}.PClkL"   (Channel, description: "Parallel lower", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_CKP_L, type: "BIAS", pageId: i)

        "${reb}.SClkU"   (Channel, description: "Serial upper", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_SCK_U, type: "BIAS", pageId: i)

        "${reb}.SClkL"   (Channel, description: "Serial lower", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_SCK_L, type: "BIAS", pageId: i)

        "${reb}.RGU"     (Channel, description: "Reset gate upper", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RG_U, type: "BIAS", pageId: i)

        "${reb}.RGL"     (Channel, description: "Reset gate lower", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RG_L, type: "BIAS", pageId: i)

        "${reb}.RefP12"  (Channel, description: "Positive 12 ref", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VP12, type: "BIAS", pageId: i)

        "${reb}.RefN12"  (Channel, description: "Negative 12 ref", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VN12, type: "BIAS", pageId: i)

        "${reb}.OD0V"    (Channel, description: "${reb} bias voltages\\OD 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OD_0, type: "BIAS", pageId: i)

        "${reb}.OG0V"    (Channel, description: "OG 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OG_0, type: "BIAS", pageId: i)

        "${reb}.RD0V"    (Channel, description: "RD 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RD_0, type: "BIAS", pageId: i)

        "${reb}.GD0V"    (Channel, description: "GD 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_GD_0, type: "BIAS", pageId: i)

        "${reb}.OD1V"    (Channel, description: "OD 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OD_1, type: "BIAS", pageId: i)

        "${reb}.OG1V"    (Channel, description: "OG 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OG_1, type: "BIAS", pageId: i)

        "${reb}.RD1V"    (Channel, description: "RD 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RD_1, type: "BIAS", pageId: i)

        "${reb}.GD1V"    (Channel, description: "GD 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_GD_1, type: "BIAS", pageId: i)

        "${reb}.OD2V"    (Channel, description: "OD 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OD_2, type: "BIAS", pageId: i)

        "${reb}.OG2V"    (Channel, description: "OG 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_OG_2, type: "BIAS", pageId: i)

        "${reb}.RD2V"    (Channel, description: "RD 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_RD_2, type: "BIAS", pageId: i)

        "${reb}.GD2V"    (Channel, description: "GD 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_GD_2, type: "BIAS", pageId: i)

        "${reb}.Ref05V"  (Channel, description: "5V ref 0 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF5_0, type: "BIAS", pageId: i)

        "${reb}.Ref15V"  (Channel, description: "5V ref 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF5_1, type: "BIAS", pageId: i)

        "${reb}.Ref25V"  (Channel, description: "5V ref 2 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF5_2, type: "BIAS", pageId: i)

        "${reb}.Ref125V" (Channel, description: "2.5V ref 1 voltage", units: "Volts",
                          devcName: "${reb}", hwChan: SlowAdcs.CHAN_VREF25_1, type: "BIAS", pageId: i)

        title = "${reb} CCD currents\\"
        for (int js = 0; js < 3; js++) {
            for (int jd = 0; jd < 2; jd++) {
                String tb = jd == 0 ? "upper" : "lower"
                for (int jc = 0; jc < 8; jc++) {
                    int ch = jc + 8 * jd + 16 * js
                    "${reb}.CCDI${js}${jd}${jc}" \
                         (Channel, description: "${title}CCD ${js} ${tb} current ${jc}", format: ".3f", units: "mA",
                          devcName: "${reb}", hwChan: ch, type: "CURR", scale: 1000, pageId: i)
                    title = ""
                }
            }
        }

    }

}
