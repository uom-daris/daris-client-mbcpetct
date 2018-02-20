package daris.client.mbc.petct;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import arc.xml.XmlStringWriter;
import daris.client.ConnectionSettings;
import daris.client.MFSession;

public class PetctDicomSftpSend implements Runnable {

    public static interface ResultHandler {
        void processed(MFSession session, int nbDatasetsSent, int totalDatasets);
    }

    public static final String APP = "mbcpetct-dicom-sftp-send";

    public static class Settings extends ConnectionSettings {

        private String _studyCID;
        private Set<String> _datasetNames;
        private Map<String, String> _elementValues;
        private String _sshHost;
        private int _sshPort;
        private String _sshUser;
        private String _sshPassword;
        private String _sshDir;

        public Settings() {
            super(null);
            super.loadFromPropertiesFile(new File(PROPERTIES_FILE));
        }

        public Settings setStudyCID(String studyCID) {
            _studyCID = studyCID;
            return this;
        }

        public String studyCID() {
            return _studyCID;
        }

        public Settings addDatasetName(String datasetName) {
            if (_datasetNames == null) {
                _datasetNames = new LinkedHashSet<String>();
            }
            _datasetNames.add(datasetName);
            return this;
        }

        public Set<String> datasetNames() {
            return _datasetNames;
        }

        public String sshServerHost() {
            return _sshHost;
        }

        public int sshServerPort() {
            return _sshPort;
        }

        public String sshUser() {
            return _sshUser;
        }

        public String sshPassword() {
            return _sshPassword;
        }

        public String sshPath() {
            if (_sshHost == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            if (_sshUser != null) {
                sb.append(_sshUser).append("@");
            }
            sb.append(_sshHost);
            if (_sshDir != null) {
                sb.append(":").append(_sshDir);
            }
            return sb.toString();
        }

        public Settings addElementValue(String tag, String value) {
            if (_elementValues == null) {
                _elementValues = new LinkedHashMap<String, String>();
            }
            _elementValues.put(tag, value);
            return this;
        }

        public Settings setSshDetails(String host, int port, String user, String directory) {
            _sshHost = host;
            _sshPort = port;
            _sshUser = user;
            return this;
        }

        public Settings setSshHost(String host) {
            _sshHost = host;
            return this;
        }

        public Settings setSshPort(int port) {
            _sshPort = port;
            return this;
        }

        public Settings setSshUser(String user) {
            _sshUser = user;
            return this;
        }

        public Settings setSshPassword(String password) {
            _sshPassword = password;
            return this;
        }

        public Settings setSshDirectory(String directory) {
            _sshDir = directory;
            return this;
        }

        public Settings setSshDetails(String sshDst) {
            String host = null;
            String user = null;
            String directory = null;
            int idx1 = sshDst.indexOf('@');
            if (idx1 >= 0) {
                host = sshDst.substring(idx1 + 1);
                user = sshDst.substring(0, idx1);
            } else {
                host = sshDst;
                user = null;
            }
            int idx2 = host.lastIndexOf(':');
            if (idx2 >= 0) {
                directory = host.substring(idx2 + 1);
                host = host.substring(0, idx2);
            }
            setSshUser(user);
            setSshHost(host);
            setSshDirectory(directory);
            return this;
        }

        public static final String PROPERTIES_FILE = new StringBuilder()
                .append(System.getProperty("user.home").replace('\\', '/')).append("/.daris/" + APP + ".properties")
                .toString();

        public void validate() throws Throwable {
            super.validate();
            if (_studyCID == null) {
                throw new IllegalArgumentException("Missing --study.cid");
            }
            if (_sshHost == null) {
                throw new IllegalArgumentException("Missing SFTP server host");
            }
            if (_sshPort == 0 || _sshPort > 65535) {
                throw new IllegalArgumentException("Invalid SFTP server port: " + _sshPort);
            }
            if (_sshUser == null) {
                throw new IllegalArgumentException("Missing SFTP user name.");
            }
            if (_sshPassword == null) {
                throw new IllegalArgumentException("Missing --password.");
            }
        }

        public Map<String, String> elementValues() {
            return _elementValues;
        }

        public String sshDirectory() {
            return _sshDir;
        }
    }

    private MFSession _session;
    private Settings _settings;
    private ResultHandler _rh;

    public PetctDicomSftpSend(Settings settings, ResultHandler rh) {
        _settings = settings;
        _session = new MFSession(settings);
        _rh = rh;
    }

    @Override
    public void run() {
        int nbDatasetsSent = 0;
        int totalDatasets = 0;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("(cid in '").append(_settings.studyCID())
                    .append("' and mf-dicom-series has value and asset has content)");
            Set<String> datasetNames = _settings.datasetNames();
            if (datasetNames != null && !datasetNames.isEmpty()) {
                sb.append(" and (");
                boolean first = true;
                for (String datasetName : datasetNames) {
                    if (datasetName != null) {
                        if (!first) {
                            sb.append(" or ");
                        }
                        if (datasetName.startsWith("*")) {
                            sb.append("(xpath(daris:pssd-object/name) ends with '" + datasetName.substring(1) + "')");
                        } else if (datasetName.endsWith("*")) {
                            sb.append("(xpath(daris:pssd-object/name) starts with '"
                                    + datasetName.substring(0, datasetName.length() - 1) + "')");
                        } else {
                            sb.append("(xpath(daris:pssd-object/name) = '" + datasetName + "')");
                        }
                        if (first) {
                            first = false;
                        }
                    }
                }
                sb.append(")");
            }

            String query = sb.toString();

            XmlStringWriter w1 = new XmlStringWriter();
            w1.add("where", query);
            w1.add("size", "infinity");
            w1.add("action", "get-cid");
            Collection<String> datasetCids = _session.execute("asset.query", w1.document(), null, null).values("cid");
            totalDatasets = datasetCids == null ? 0 : datasetCids.size();

            if (datasetCids == null || datasetCids.isEmpty()) {
                System.out.println("No DICOM datasets found!");
                return;
            }
            for (String datasetCid : datasetCids) {
                sendDicomDataset(datasetCid);
                nbDatasetsSent++;
            }
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        } finally {
            if (_rh != null) {
                _rh.processed(_session, nbDatasetsSent, totalDatasets);
            }
            _session.discard();
        }

    }

    private void sendDicomDataset(String datasetCID) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("cid", datasetCID);
        w.add("host", _settings.sshServerHost());
        if (_settings.sshServerPort() > 0) {
            w.add("port", _settings.sshServerPort());
        }
        if (_settings.sshDirectory() != null) {
            w.add("directory", _settings.sshDirectory());
        }
        w.add("user", _settings.sshUser());
        w.add("password", _settings.password());
        Map<String, String> elementValues = _settings.elementValues();
        if (elementValues != null && !elementValues.isEmpty()) {
            w.push("override");
            Set<String> tags = elementValues.keySet();
            for (String tag : tags) {
                String value = elementValues.get(tag);
                if (value == null) {
                    w.add("element", new String[] { "tag", tag, "anonymize", "true" });
                } else {
                    w.push("element", new String[] { "tag", tag });
                    w.add("value", value);
                    w.pop();
                }
            }
            w.pop();
        }
        System.out.print("Sending dataset " + datasetCID + "...");
        _session.execute("daris.dicom.sftp.send", w.document(), null, null);
        System.out.println("done.");
    }

}
