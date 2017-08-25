@echo off

cmd /k java -cp "%~dp0\daris-mbcpetct-dicom-onsend.jar" daris.client.mbc.petct.PetctDicomOnsendCLI %*
