package daris.client.mbc.petct;

public class PetctRawStudyCopyCLI {

    public static void main(String[] args) throws Throwable {

        /*
         * load, parse & validate settings
         */
        PetctRawStudyCopy.Settings settings = new PetctRawStudyCopy.Settings();
        try {
            if (args.length < 2) {
                throw new IllegalArgumentException("Missing arguments.");
            }
            settings.setStudyCID(args[args.length - 2]);
            settings.setDstPID(args[args.length - 1]);
            for (int i = 0; i < args.length - 2;) {
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
                } else if (args[i].equals("--no-anonymize")) {
                    settings.setAnonymize(false);
                    i++;
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

        PetctRawStudyCopy studyCopy = new PetctRawStudyCopy(settings);
        studyCopy.run();
    }

    private static void printUsage() {
        // @formatter:off
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("    "+ PetctRawStudyCopy.APP + " [options] <src-study-cid> <dst-parent-cid>");
        System.out.println();
        System.out.println("DESCRIPTION:");
        System.out.println("    " + PetctRawStudyCopy.APP + " is client application to copy a raw MBC PET/CT study to another subject/ex-method.");
        System.out.println();
        System.out.println("OPTIONS:");
        System.out.println("    --help                               Display help information.");
        System.out.println("    --mf.host <host>                     The Mediaflux server host.");
        System.out.println("    --mf.port <port>                     The Mediaflux server port.");
        System.out.println("    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.");
        System.out.println("    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.");
        System.out.println("    --mf.token <token>                   The Mediaflux secure identity token.");
        System.out.println("    --no-anonymize                       Do not remove patient name from the file name.");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("    " + PetctRawStudyCopy.APP + " --mf.host daris-1.cloud.unimelb.edu.au --mf.port 443 --mf.transport https --mf.auth mbic,williamsr,PASSWD 1.7.6.115.1.2 1.5.1.91.1");
        System.out.println();
        // @formatter:on
    }

}
