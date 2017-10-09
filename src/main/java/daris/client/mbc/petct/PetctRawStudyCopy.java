package daris.client.mbc.petct;

import java.util.Collection;

import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import daris.client.ConnectionSettings;
import daris.client.MFSession;
import daris.client.pssd.CiteableIdUtils;
import daris.client.util.AssetUtils;

public class PetctRawStudyCopy implements Runnable {

    public static final String APP = "mbcpetct-raw-study-copy";

    public static class Settings extends ConnectionSettings {

        private String _studyCID;
        private String _dstPID;
        private boolean _anonymize;

        public Settings() {
            super(null);
            _studyCID = null;
            _dstPID = null;
            _anonymize = false;
        }

        public void setStudyCID(String cid) {
            _studyCID = cid;
        }

        public void setDstPID(String dstPID) {
            _dstPID = dstPID;
        }

        public void setAnonymize(boolean anonymize) {
            _anonymize = anonymize;
        }

        public String studyCID() {
            return _studyCID;
        }

        public String dstPID() {
            return _dstPID;
        }

        public boolean anonymize() {
            return _anonymize;
        }

        public void validate() throws Throwable {
            super.validate();
            if (_studyCID == null) {
                throw new IllegalArgumentException("Missing src-study-cid argument.");
            }
            if (_dstPID == null) {
                throw new IllegalArgumentException("Missing dst-parent-cid argument.");
            }
        }

    }

    private Settings _settings;
    private MFSession _session;

    public PetctRawStudyCopy(Settings settings) {
        _settings = settings;
        _session = new MFSession(settings);
    }

    @Override
    public void run() {
        try {

            XmlDoc.Element studyAE = AssetUtils.getAssetMeta(_session, null, _settings.studyCID());

            // validate the source raw study
            if (!studyAE.elementExists("meta/daris:pssd-study")) {
                throw new Exception("Asset " + _settings.studyCID()
                        + " is not a valid DaRIS study. No daris:pssd-study document is found.");
            }
            if (!studyAE.elementExists("meta/daris:siemens-raw-petct-study")) {
                throw new Exception("Asset " + _settings.studyCID()
                        + " is not a valid Siemens PET/CT raw study. No daris:siemens-raw-petct-study document is found.");
            }

            // identify the dst subject/ex-method
            XmlDoc.Element dstParentAE = AssetUtils.getAssetMeta(_session, null, _settings.dstPID());
            String dstParentType = dstParentAE.value("meta/daris:pssd-object/type");
            String dstSubjectCID = null;
            String dstExMethodCID = null;
            if ("ex-method".equals(dstParentType)) {
                dstExMethodCID = _settings.dstPID();
                dstSubjectCID = CiteableIdUtils.getParentCID(dstExMethodCID);
            } else if ("subject".equals(dstParentType)) {
                dstSubjectCID = _settings.dstPID();
                XmlStringWriter w = new XmlStringWriter();
                w.add("where", "cid in '" + dstSubjectCID + "'");
                w.add("size", "infinity");
                w.add("action", "get-cid");
                Collection<String> exMethodCIDs = _session.execute("asset.query", w.document(), null, null)
                        .values("cid");
                if (exMethodCIDs == null || exMethodCIDs.isEmpty()) {
                    throw new Exception("No ex-method found in dst subject " + dstSubjectCID + ".");
                }
                if (exMethodCIDs.size() > 1) {
                    throw new Exception("Multiple ex-method found in dst subject " + dstSubjectCID
                            + ". You need to specify the ex-method id.");
                }
                dstExMethodCID = exMethodCIDs.iterator().next();
            } else {
                throw new Exception("Destination parent asset " + _settings.dstPID()
                        + " is not a valid DaRIS subject or ex-method.");
            }

            // copy study
            XmlStringWriter w = new XmlStringWriter();
            w.add("cid", _settings.studyCID());
            w.add("to", dstExMethodCID);
            XmlDoc.Element re = _session.execute("daris.study.copy", w.document(), null, null);
            String dstStudyCID = re.value("study/@cid");
            System.out.println("created study " + dstStudyCID);
            Collection<String> dstDatasetCIDs = re.values("study/dataset/@cid");
            if (dstDatasetCIDs != null) {
                for (String dstDatasetCID : dstDatasetCIDs) {
                    anonymizePetctRawDataset(_session, dstDatasetCID);
                    System.out.println("created dataset " + dstDatasetCID);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        }
    }

    private static void anonymizePetctRawDataset(MFSession session, String datasetCID) throws Throwable {
        XmlDoc.Element datasetAE = AssetUtils.getAssetMeta(session, null, datasetCID);
        String datasetAssetID = datasetAE.value("@id");
        String docID = datasetAE.value("meta/daris:pssd-filename/@id");
        String fileName = datasetAE.value("meta/daris:pssd-filename/original");
        if (fileName != null) {
            int idx1 = fileName.indexOf(".CT.PET");
            int idx2 = fileName.indexOf(".PT.PET");
            if (idx1 != -1 || idx2 != -1) {
                String newFileName = datasetCID + fileName.substring(idx1 != -1 ? idx1 : idx2);
                XmlStringWriter w = new XmlStringWriter();

                w.push("service", new String[] { "name", "asset.set" });
                w.add("cid", datasetCID);
                w.push("meta");
                w.push("daris:pssd-filename", new String[] { "id", docID });
                w.add("original", newFileName);
                w.pop();
                w.pop();
                w.pop();

                w.push("service", new String[] { "name", "asset.prune" });
                w.add("id", datasetAssetID);
                w.add("retain", 1);
                w.pop();

                w.add("atomic", true);

                session.execute("service.execute", w.document(), null, null);
            }
        }

    }

}
