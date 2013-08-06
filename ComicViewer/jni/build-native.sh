#!/bin/sh

if ! which ndk-build >/dev/null 2>&1; then
	echo "ndk-build not found in PATH"
	exit 1
fi

SCRIPTDIR=`dirname $0`
MUPDF=mupdf-cff6f809da556624fb1de34725935278093182e1
FREETYPE=freetype-2.4.10
OPENJPEG=openjpeg-2.0.0
JBIG2DEC=jbig2dec-0.11
JPEGSRC=jpegsrc.v8d.tar.gz
JPEGDIR=jpeg-8d

cd "$SCRIPTDIR/../deps"

echo "extracting deps"
tar xf $FREETYPE.tar.bz2 && echo "freetype extracted" &
tar xf $JPEGSRC && echo "jpeg extracted" &
(unxz < $MUPDF.tar.xz | tar -xf -) && echo "mupdf extracted" &
tar xf $OPENJPEG.tar.gz && echo "openjpeg extracted" &
tar xf $JBIG2DEC.tar.gz && echo "jbig2dec extracted" &
wait

echo "copying openjpeg"
cp $OPENJPEG/src/lib/openjp2/*.[ch] ../jni/openjpeg/
cp opj_config.h ../jni/openjpeg/.

echo "copying jpeg"
cp $JPEGDIR/*.[ch] ../jni/jpeg/

echo "copying jbig2dec"
cp $JBIG2DEC/* ../jni/jbig2dec/

echo "copying mupdf"
for x in draw fitz pdf ; do
    cp -r $MUPDF/$x/*.[ch] ../jni/mupdf/$x/
done

echo "patching mupdf"
cd ..
patch jni/mupdf/fitz/fitz.h jni/mupdf-apv/fitz/apv_fitz.h.patch
patch -o jni/mupdf-apv/fitz/apv_doc_document.c jni/mupdf/fitz/doc_document.c jni/mupdf-apv/fitz/apv_doc_document.c.patch
patch -o jni/mupdf-apv/pdf/apv_pdf_cmap_table.c jni/mupdf/pdf/pdf_cmap_table.c jni/mupdf-apv/pdf/apv_pdf_cmap_table.c.patch
patch -o jni/mupdf-apv/pdf/apv_pdf_fontfile.c jni/mupdf/pdf/pdf_fontfile.c jni/mupdf-apv/pdf/apv_pdf_fontfile.c.patch
cd deps


echo "copying freetype"
cp -r $FREETYPE/src ../jni/freetype/
cp -r $FREETYPE/include ../jni/freetype/
cd ..

echo "running ndk-build"
ndk-build

echo "build-native done"
