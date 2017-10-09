package daris.client.util;

import arc.xml.XmlDoc;
import arc.xml.XmlStringWriter;
import daris.client.MFSession;

public class AssetUtils {

    public static XmlDoc.Element getAssetMeta(MFSession session, String id, String cid) throws Throwable {
        XmlStringWriter w = new XmlStringWriter();
        if (id == null && cid == null) {
            throw new IllegalArgumentException("No asset id or cid are specified.");
        }
        if (id != null) {
            w.add("id", id);
        } else {
            w.add("cid", cid);
        }
        return session.execute("asset.get", w.document(), null, null).element("asset");
    }

}
