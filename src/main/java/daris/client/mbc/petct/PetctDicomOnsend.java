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

public class PetctDicomOnsend implements Runnable {

    public static interface ResultHandler {
        void processed(int nbDatasetsSent, int totalDatasets);
    }

    public static final String APP = "mbcpetct-dicom-onsend";

    public static class Settings extends ConnectionSettings {

        private String _studyCID;
        private Set<String> _datasetNames;
        private Map<String, String> _elementValues;
        private String _callingAET;
        private String _calledAET;
        private String _calledHost;
        private int _calledPort = -1;

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

        public String calledAETitle() {
            return _calledAET;
        }

        public String calledAEHost() {
            return _calledHost;
        }

        public int calledAEPort() {
            return _calledPort;
        }

        public String callingAETitle() {
            return _callingAET;
        }

        public Settings addElementValue(String tag, String value) {
            if (_elementValues == null) {
                _elementValues = new LinkedHashMap<String, String>();
            }
            _elementValues.put(tag, value);
            return this;
        }

        public Settings setCalledApplicationEntity(String host, int port, String aeTitle) {
            _calledHost = host;
            _calledPort = port;
            _calledAET = aeTitle;
            return this;
        }

        public Settings setCalledAETitle(String aeTitle) {
            _calledAET = aeTitle;
            return this;
        }

        public Settings setCalledAEHost(String host) {
            _calledHost = host;
            return this;
        }

        public Settings setCalledAEPort(int port) {
            _calledPort = port;
            return this;
        }

        public Settings setCallingAETitle(String aeTitle) {
            _callingAET = aeTitle;
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
            if (_callingAET == null) {
                throw new IllegalArgumentException("Missing --calling.ae.title");
            }
            if (_calledAET == null) {
                throw new IllegalArgumentException("Missing --called.ae.title");
            }
            if (_calledHost == null) {
                throw new IllegalArgumentException("Missing --called.ae.host");
            }
            if (_calledPort < 0) {
                throw new IllegalArgumentException("Missing --called.ae.port");
            }
        }

        public Map<String, String> elementValues() {
            return _elementValues;
        }
    }

    private MFSession _session;
    private Settings _settings;
    private ResultHandler _rh;

    public PetctDicomOnsend(Settings settings, ResultHandler rh) {
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
                _rh.processed(nbDatasetsSent, totalDatasets);
            }
        }

    }

    private void sendDicomDataset(String datasetCID) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("cid", datasetCID);
        w.push("calling-ae");
        w.add("title", _settings.callingAETitle());
        w.pop();
        w.push("called-ae");
        w.add("host", _settings.calledAEHost());
        w.add("port", _settings.calledAEPort());
        w.add("title", _settings.calledAETitle());
        w.pop();
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
        _session.execute("daris.dicom.send", w.document(), null, null);
        System.out.println("done.");
    }

}
