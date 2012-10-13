#!/bin/sh
# make sure ndk-build is in path

SCRIPTDIR=`dirname $0`
MUPDF_FILE=mupdf-snapshot-20111207.tar.gz
MUPDF=mupdf
#MUPDF_FILE=mupdf-0.9-source.tar.gz
#MUPDF=mupdf-0.9
FREETYPE=freetype-2.4.6
OPENJPEG=openjpeg_v1_4_sources_r697
JBIG2DEC=jbig2dec-0.11
JPEGSRC=jpegsrc.v8a.tar.gz
JPEGDIR=jpeg-8a

cd $SCRIPTDIR/../deps
tar xvf $FREETYPE.tar.bz2
tar xvf $JPEGSRC
tar xvf $MUPDF_FILE
tar xvf $OPENJPEG.tgz
tar xvf $JBIG2DEC.tar.gz
cp $OPENJPEG/libopenjpeg/*.[ch] ../jni/openjpeg/
echo '#define PACKAGE_VERSION' '"'$OPENJPEG'"' > ../jni/openjpeg/opj_config.h
cp $JPEGDIR/*.[ch] ../jni/jpeg/
cp $JBIG2DEC/* ../jni/jbig2dec/
for x in draw fitz pdf ; do
    cp -r $MUPDF/$x/*.[ch] ../jni/mupdf/$x/
done
cp -r $MUPDF/fonts ../jni/mupdf/
cp -r $FREETYPE/{src,include} ../jni/freetype/
gcc -o ../scripts/fontdump $MUPDF/scripts/fontdump.c
cd ../jni/mupdf
mkdir generated 2> /dev/null
../../scripts/fontdump generated/font_base14.h fonts/*.cff
../../scripts/fontdump generated/font_droid.h fonts/droid/DroidSans.ttf fonts/droid/DroidSansMono.ttf
cd ..
ndk-build
