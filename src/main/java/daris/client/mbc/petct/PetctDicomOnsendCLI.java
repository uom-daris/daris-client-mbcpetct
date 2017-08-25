package daris.client.mbc.petct;

public class PetctDicomOnsendCLI {

    public static void main(String[] args) throws Throwable {

        /*
         * load, parse & validate settings
         */
        PetctDicomOnsend.Settings settings = new PetctDicomOnsend.Settings();
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
                } else if (args[i].equals("--calling-aet")) {
                    settings.setCallingAETitle(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--called-ae")) {
                    addCalledApplicationEntity(settings, args[i + 1]);
                    i += 2;
                } else {
                    throw new IllegalArgumentException("Invalid arguments.");
                }
            }
            settings.validate();
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            printUsage();
            System.exit(1);
        }

        PetctDicomOnsend onsend = new PetctDicomOnsend(settings, new PetctDicomOnsend.ResultHandler() {

            @Override
            public void processed(int nbDatasetsSent, int totalDatasets) {
                System.out.println("Sent " + nbDatasetsSent + "/" + totalDatasets + " datasets.");
            }
        });
        onsend.run();
    }

    private static void addDicomElementValue(PetctDicomOnsend.Settings settings, String s) throws Throwable {
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

    private static void addCalledApplicationEntity(PetctDicomOnsend.Settings settings, String s) throws Throwable {
        if (s != null) {
            String[] parts1 = s.split("@");
            if (parts1 != null && parts1.length == 2) {
                String title = parts1[0];
                if (parts1[1] != null) {
                    String[] parts2 = parts1[1].split(":");
                    if (parts2 != null && parts2.length == 2) {
                        String host = parts2[0];
                        Integer port = null;
                        try {
                            port = Integer.parseInt(parts2[1]);
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException("Invalid --called-ae: " + s, nfe);
                        }
                        if (title != null && host != null && port != null) {
                            settings.setCalledApplicationEntity(host, port, title);
                            return;
                        }
                    }
                }
            }
        }
        throw new IllegalArgumentException("Invalid --called-ae: " + s);
    }

    private static void printUsage() {
        // @formatter:off
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("    "+ PetctDicomOnsend.APP + " [options]");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("    " + PetctDicomOnsend.APP + " is client application to select DICOM dataset in the specified study and send them to remote DICOM server.");
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
        System.out.println("    --calling-aet <ae-title>             The calling application entity title.");
        System.out.println("    --called-ae <title@host:port>        The called application entity, in the form of title@host:port. Example: DARIS@daris.vidnode.org.au:104");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("    " + PetctDicomOnsend.APP + " --mf.host daris-1.cloud.unimelb.edu.au --mf.port 443 --mf.transport https --mf.auth mbic,williamsr,PASSWD --study.cid 1.7.46.1.1.1 --dataset.name early_ct_49_ac --dataset.name early_50_70_fbp_dyn --calling-aet MBC_DARIS --called-ae AE@remote.dicom.server.org:11112");
        System.out.println();
        // @formatter:on
    }

}
