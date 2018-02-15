package daris.client.mbc.petct;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import arc.archive.ArchiveInput;
import arc.archive.ArchiveRegistry;
import arc.mf.client.archive.Archive;
import arc.mime.NamedMimeType;
import arc.streams.LongInputStream;
import arc.streams.StreamCopy;
import arc.xml.XmlDoc;
import arc.xml.XmlDoc.Element;
import arc.xml.XmlStringWriter;
import daris.client.ConnectionSettings;
import daris.client.MFSession;

public class PetctDicom2NiftiDownload implements Runnable {

    public static final String APP = "mbcpetct-dicom2nifti-download";
    public static final int DEFAULT_PAGE_SIZE = 1000;

    public static class Settings extends ConnectionSettings {

        private Set<String> _pids;
        private File _dstDir;
        private String _filter;

        public Settings() {
            super(null);
            super.loadFromPropertiesFile(new File(PROPERTIES_FILE));
            _pids = new LinkedHashSet<String>();
        }

        public Settings addParentCID(String pid) {
            _pids.add(pid);
            return this;
        }

        public Collection<String> parentCIDs() {
            return _pids;
        }

        public Settings setDstDir(File dstDir) {
            _dstDir = dstDir;
            return this;
        }

        public File dstDir() {
            return _dstDir;
        }

        public String filter() {
            return _filter;
        }

        public Settings setFilter(String filter) {
            _filter = filter;
            return this;
        }

        public static final String PROPERTIES_FILE = new StringBuilder()
                .append(System.getProperty("user.home").replace('\\', '/')).append("/.daris/" + APP + ".properties")
                .toString();

        public void validate() throws Throwable {
            super.validate();
            if (_pids.isEmpty()) {
                throw new IllegalArgumentException("Missing DaRIS id");
            }
            if (_dstDir == null) {
                throw new IllegalArgumentException("Missing --dir.");
            }
            if (!_dstDir.exists()) {
                throw new IllegalArgumentException(_dstDir.getAbsolutePath() + " does not exists.");
            }
            if (!_dstDir.isDirectory()) {
                throw new IllegalArgumentException(_dstDir.getAbsolutePath() + " is not directory.");
            }
        }

    }

    private Settings _settings;
    private MFSession _session;

    public PetctDicom2NiftiDownload(Settings settings) {
        _settings = settings;
        _session = new MFSession(settings);
    }

    @Override
    public void run() {
        try {
            execute();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void execute() throws Throwable {
        Archive.declareSupportForAllTypes();
        long idx = 1;
        boolean complete = false;
        StringBuilder sb = new StringBuilder();
        sb.append("type='dicom/series' and asset has content");
        if (_settings.filter() != null) {
            sb.append(" and (").append(_settings.filter()).append(")");
        }
        if (_settings.parentCIDs().size() == 1) {
            String cid = _settings.parentCIDs().iterator().next();
            sb.append(" and (cid='" + cid + "' or cid starts with '" + cid + "')");
        } else {
            sb.append(" and (");
            int i = 0;
            for (String cid : _settings.parentCIDs()) {
                if (i > 0) {
                    sb.append(" or ");
                }
                sb.append("(cid='" + cid + "' or cid starts with '" + cid + "')");
                i++;
            }
            sb.append(")");
        }
        String where = sb.toString();
        do {
            XmlStringWriter w = new XmlStringWriter();
            w.add("where", where);
            w.add("idx", idx);
            w.add("size", DEFAULT_PAGE_SIZE);
            w.add("action", "get-cid");
            XmlDoc.Element re = _session.execute("asset.query", w.document(), null, null);
            List<XmlDoc.Element> cides = re.elements("cid");
            if (cides != null) {
                for (XmlDoc.Element cide : cides) {
                    transcode(cide.value(), cide.value("@id"));
                }
            }
            idx += DEFAULT_PAGE_SIZE;
            complete = re.booleanValue("cursor/total/@complete");
        } while (!complete);
    }

    private String getMbciuId(String cid) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        w.add("where", "cid contains (cid='" + cid + "') and model='om.pssd.study'");
        w.add("action", "get-value");
        w.add("size", 1);
        w.add("xpath", new String[] { "ename", "other-id" },
                "daris:pssd-study/other-id[@type='Melbourne Brain Centre Imaging Unit']");
        return _session.execute("asset.query", w.document(), null, null).value("asset/other-id");
    }

    private void transcode(String cid, String id) throws Throwable {
        String mbciuId = getMbciuId(cid);
        final String outputFileNamePrefix = (mbciuId == null ? "" : (mbciuId + "_")) + cid + "_";
        XmlStringWriter w = new XmlStringWriter();
        w.add("id", id);
        w.add("atype", "zip");
        w.add("clevel", 0);
        w.push("transcode");
        w.add("from", "dicom/series");
        w.add("to", "nifti/series");
        w.pop();
        _session.execute("asset.transcode", w.document(), null, new arc.mf.client.ServerClient.OutputConsumer() {

            @Override
            protected void consume(Element xe, LongInputStream in) throws Throwable {
                ArchiveInput ai = ArchiveRegistry.createInput(in, new NamedMimeType("application/zip"));
                try {
                    ArchiveInput.Entry e;
                    int i = 1;
                    while ((e = ai.next()) != null) {
                        try {
                            if (!e.isDirectory()) {
                                String fname = null;
                                if (e.name().toLowerCase().endsWith(".nii")) {
                                    fname = outputFileNamePrefix + i + ".nii";
                                } else if (e.name().toLowerCase().endsWith(".nii.gz")) {
                                    fname = outputFileNamePrefix + i + ".nii.gz";
                                } else {
                                    fname = outputFileNamePrefix + e.name();
                                }
                                if (fname != null) {
                                    File f = new File(_settings.dstDir(), fname);
                                    System.out.print("Downloading '" + f.getAbsolutePath() + "' ...");
                                    StreamCopy.copy(e.stream(), f);
                                    System.out.println("done.");
                                    i++;
                                }
                            }
                        } finally {
                            ai.closeEntry();
                        }
                    }
                } finally {
                    ai.close();
                    in.close();
                }
            }
        });
    }

}
