# daris-mbcpetct-dicom-onsend
A client application to select dicom data from daris and onsend to remote dicom server.

```
USAGE:
    mbcpetct-dicom-onsend [options]

DESCRIPTION:
    mbcpetct-dicom-onsend is client application to select DICOM dataset in the specified study and send them to remote DICOM server.

OPTIONS:
    --help                               Display help information.
    --mf.host <host>                     The Mediaflux server host.
    --mf.port <port>                     The Mediaflux server port.
    --mf.transport <transport>           The Mediaflux server transport, can be http, https or tcp/ip.
    --mf.auth <domain,user,password>     The Mediaflux user authentication deatils.
    --mf.token <token>                   The Mediaflux secure identity token.
    --study.cid <study-cid>              The DaRIS id of the DICOM study.
    --dataset.name <dataset-name>        The names of the DICOM dataset to select within the specified study. Can occur multiple times to select more than one dataset.
    --element <tag=value>                The DICOM element to set, in the form of ggggeeee=value. Can occur multiple times to set more than one DICOM elements. Example: 00100020=1.7.46.1 
    --calling-aet <ae-title>             The calling application entity title.
    --called-ae <title@host:port>        The called application entity, in the form of title@host:port. Example: DARIS@daris.vidnode.org.au:104

EXAMPLES:
    mbcpetct-dicom-onsend --mf.host daris-1.cloud.unimelb.edu.au --mf.port 443 --mf.transport https --mf.auth mbic,williamsr,PASSWD --study.cid 1.7.46.1.1.1 --dataset.name early_ct_49_ac --dataset.name early_50_70_fbp_dyn --calling-aet MBC_DARIS --called-ae AE@remote.dicom.server.org:11112
```
