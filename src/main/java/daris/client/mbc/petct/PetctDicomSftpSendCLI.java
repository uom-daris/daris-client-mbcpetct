package daris.client.mbc.petct;

import java.util.Date;

import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import daris.client.MFSession;
import mbciu.mbc.MBCFMP;
import nig.mf.pssd.CiteableIdUtil;

public class PetctDicomSftpSendCLI {
    // Credential path for access FileMAkerPro data base
    private static final String FMP_CRED_REL_PATH = "/.fmp/mbciu_fmp";

    public static void main(String[] args) throws Throwable {

        /*
         * load, parse & validate settings
         */
        PetctDicomSftpSend.Settings settings = new PetctDicomSftpSend.Settings();
        try {
            for (int i = 0; i < args.length;) {
                if (args[i].equals("--help") || args[i].equals("-h")) {
                    printUsage();
                    System.exit(0);
                } else if (args[i].equals("--mf.host")) {
                    settings.setServerHost(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--mf.port")) {
                    try {
                        settings.setServerPort(Integer.parseInt(args[i + 1]));
                    } catch (Throwable e) {
                        throw new IllegalArgumentException("Invalid mf.port: " + args[i + 1], e);
                    }
                    i += 2;
                } else if (args[i].equals("--mf.transport")) {
                    settings.setServerTransport(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--mf.auth")) {
                    String auth = args[i + 1];
                    String[] parts = auth.split(",");
                    if (parts == null || parts.length != 3) {
                        throw new IllegalArgumentException("Invalid mf.auth: " + auth);
                    }
                    settings.setUserCredentials(parts[0], parts[1], parts[2]);
                    i += 2;
                } else if (args[i].equals("--mf.token")) {
                    settings.setToken(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--mf.sid")) {
                    settings.setSessionKey(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--study.cid")) {
                    settings.setStudyCID(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--dataset.name")) {
                    settings.addDatasetName(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--element")) {
                    addDicomElementValue(settings, args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--ssh.port")) {
                    settings.setSshPort(Integer.parseInt(args[i + 1]));
                    i += 2;
                } else if (args[i].equals("--ssh.password")) {
                    settings.setSshPassword(args[i + 1]);
                    i += 2;
                } else {
                    if (settings.sshServerHost() == null) {
                        settings.setSshDetails(args[i]);
                        i++;
                    } else {
                        throw new IllegalArgumentException("Invalid arguments.");
                    }
                }
            }
            settings.validate();
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            printUsage();
            System.exit(1);
        }

        PetctDicomSftpSend onsend = new PetctDicomSftpSend(settings, new PetctDicomSftpSend.ResultHandler() {

            private void updateFMP(MFSession session, int nsent, int ntot) {
                // Write results to FMP

                try {
                    // Need to fetch values from data
                    String studyCID = settings.studyCID();

                    // Get Subject CID and fetch Patient ID
                    String subjectCID = CiteableIdUtil.getSubjectId(studyCID);

                    // Get the Date from the Study
                    XmlStringWriter w = new XmlStringWriter();
                    w.add("id", studyCID);
                    XmlDoc.Element r = session.execute("om.pssd.object.describe", w.document(), null, null);
                    Date date = r.dateValue("object/meta/mf-dicom-study/sdate");
                    String visitID = r.value("object/other-id[@type='Melbourne Brain Centre Imaging Unit']");

                    // Get the visit ID from Study/other-id. It's not there for
                    // older data.
                    // We did not start passing it through the Accession Number
                    // and thence
                    // to the daris:pssd-study/other-id until Nov 2017
                    w = new XmlStringWriter();
                    w.add("id", subjectCID);
                    r = session.execute("om.pssd.object.describe", w.document(), null, null);
                    XmlDoc.Element dicom = r.element("object/private/mf-dicom-patient");
                    String patientID = null;
                    if (dicom != null) {
                        patientID = dicom.value("id");
                    }
                    /*
                     * System.out.println("patient ID = " + patientID);
                     * System.out.println("date = " + date);
                     * System.out.println("visit ID = " + visitID);
                     */
                    // Update FMP
                    if (patientID != null && (date != null || visitID != null)) {
                        System.out.println("Accessing FMP");
                        String home = System.getProperty("user.home");
                        MBCFMP mbc = new MBCFMP(home + FMP_CRED_REL_PATH);
                        String fieldValue = "Sent " + settings.sshPath() + " with " + nsent
                                + " DataSets from a total of " + ntot + " DataSets";
                        String fieldName = "Dicomsendsuccess";
                        String visitType = "PETCT";
                        mbc.updateStringInVisit(patientID, date, visitID, fieldName, fieldValue, visitType, false);
                        mbc.closeConnection();
                        System.out.println("Successfully updated FMP");
                    } else {
                        System.out.println(
                                "Did not update FMP - could not extract Ptaient ID, Date Acquisition and/or Visit ID from the data");
                    }
                } catch (Throwable t) {
                    System.out.println("Error updating FileMakerPro DataBase with the number of sent DataSets : "
                            + t.getMessage());
                }
            }

            @Override
            public void processed(MFSession session, int nbDatasetsSent, int totalDatasets) {
                System.out.println("Sent " + nbDatasetsSent + "/" + totalDatasets + " datasets.");
                updateFMP(session, nbDatasetsSent, totalDatasets);

            }
        });
        onsend.run();
    }

    private static void addDicomElementValue(PetctDicomSftpSend.Settings settings, String s) throws Throwable {
        if (s != null && s.indexOf('=') != -1) {
            String[] parts = s.split("=");
            if (parts != null && (parts.length == 1 || parts.length == 2)) {
                String tag = parts[0];
                String value = parts.length == 1 ? null : parts[1];
                if (value != null) {
                    value = value.trim();
                    if (value.isEmpty()) {
                        value = null;
                    }
                }
                if (tag.matches("^\\d{8}$")) {
                    settings.addElementValue(tag, value);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Invalid --element: " + s);
    }

    private static void printUsage() {
        // @formatter:off
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("    "+ PetctDicomSftpSend.APP + " [options] [ssh_user@]ssh_host[:path]");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("    " + PetctDicomSftpSend.APP + " is client application to select DICOM dataset in the specified study and send them to remote SSH server via sftp. It also writes back the status of the transfers into the MBC's FileMakerPro.");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("    --help                               Display help information.");
        System.out.println("    --mf.host <host>                     The Mediaflux server host.");
        System.out.println("    --mf.port <port>                     The Mediaflux server port.");
        System.out.println("    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.");
        System.out.println("    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.");
        System.out.println("    --mf.token <token>                   The Mediaflux secure identity token.");
        System.out.println("    --study.cid <study-cid>              The DaRIS id of the DICOM study.");
        System.out.println("    --dataset.name <dataset-name>        The names of the DICOM dataset to select within the specified study. Can occur multiple times to select more than one dataset.");
        System.out.println("    --element <tag=value>                The DICOM element to set, in the form of ggggeeee=value. Can occur multiple times to set more than one DICOM elements. Example: 00100020=1.7.46.1 ");
        System.out.println("    --ssh.password <ssh_password>        The password of the SSH user.");
        System.out.println("    --ssh.port <ssh_port>                The SSH server port. Defaults to 22.");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("    " + PetctDicomSftpSend.APP + " --mf.host daris-1.cloud.unimelb.edu.au --mf.port 443 --mf.transport https --mf.auth mbic,williamsr,PASSWD --study.cid 1.7.46.1.1.1 --dataset.name early_ct_49_ac --dataset.name early_50_70_fbp_dyn --ssh.password YOUR_SSH_PASS --ssh.port 22 SSH_USER@ssh-host.org:/dst-dir");
        System.out.println();
        // @formatter:on
    }
}