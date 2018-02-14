package daris.client.mbc.petct;

import java.io.File;

public class PetctDicom2NiftiDownloadCLI {

    public static void main(String[] args) throws Throwable {

        /*
         * load, parse & validate settings
         */
        PetctDicom2NiftiDownload.Settings settings = new PetctDicom2NiftiDownload.Settings();
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
                } else if (args[i].equals("--filter")) {
                    settings.setFilter(args[i + 1]);
                    i += 2;
                } else if (args[i].equals("--dir")) {
                    settings.setDstDir(new File(args[i + 1]));
                    i += 2;
                } else {
                    settings.addParentCID(args[i]);
                    i++;
                }
            }
            if (settings.dstDir() == null) {
                settings.setDstDir(new File(System.getProperty("user.dir")));
            }
            settings.validate();
        } catch (IllegalArgumentException ex) {
            System.err.println("Error: " + ex.getMessage());
            printUsage();
            System.exit(1);
        }
        new PetctDicom2NiftiDownload(settings).run();
    }

    private static void printUsage() {
        // @formatter:off
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("    "+ PetctDicom2NiftiDownload.APP + " [options] <daris-id>");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("    " + PetctDicom2NiftiDownload.APP + " is client application to convert dicom datasets to NIFTI files and download to local directory.");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("    --help                               Display help information.");
        System.out.println("    --mf.host <host>                     The Mediaflux server host.");
        System.out.println("    --mf.port <port>                     The Mediaflux server port.");
        System.out.println("    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.");
        System.out.println("    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.");
        System.out.println("    --mf.token <token>                   The Mediaflux secure identity token.");
        System.out.println("    --filter <filter>                    Query to select the data sets.");
        System.out.println("    --dir <output-dir>                   Output directory. If not specified, use the current directory.");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("    " + PetctDicom2NiftiDownload.APP + " --mf.host daris-1.cloud.unimelb.edu.au --mf.port 443 --mf.transport https --mf.auth mbic,williamsr,PASSWD --dir ~/Downloads/petct-nifti-files/ 1.7.61.1");
        System.out.println();
        // @formatter:on
    }

}
