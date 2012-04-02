#include "fitz.h"

#include <libgen.h>
#include "android/log.h"

char fz_errorbuf[150*20] = {0};
static int fz_errorlen = 0;
static int fz_errorclear = 1;

static void
fz_printerror(int type, const char *file, int line, const char *func, char *msg)
{
	char buf[150];
	int len;
	char *s;

	s = strrchr(file, '\\');
	if (s)
		file = s + 1;

	__android_log_print(ANDROID_LOG_INFO, "mupdf", "%c %s:%d: %s(): %s\n", type, basename(file), line, func, msg);

	snprintf(buf, sizeof buf, "%s:%d: %s(): %s", basename(file), line, func, msg);
	buf[sizeof(buf)-1] = 0;
	len = strlen(buf);

	if (fz_errorclear)
	{
		fz_errorclear = 0;
		fz_errorlen = 0;
		memset(fz_errorbuf, 0, sizeof fz_errorbuf);
	}

	if (fz_errorlen + len + 2 < sizeof fz_errorbuf)
	{
		memcpy(fz_errorbuf + fz_errorlen, buf, len);
		fz_errorlen += len;
		fz_errorbuf[fz_errorlen++] = '\n';
		fz_errorbuf[fz_errorlen] = 0;
	}
}

void fz_warn(char *fmt, ...)
{
	va_list ap;
	fprintf(stderr, "warning: ");
	va_start(ap, fmt);
	vfprintf(stderr, fmt, ap);
	va_end(ap);
	fprintf(stderr, "\n");
}

fz_error fz_throwimp(const char *file, int line, const char *func, char *fmt, ...)
{
	char buf[150];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(buf, sizeof buf, fmt, ap);
	va_end(ap);
	buf[sizeof(buf)-1] = 0;
	fz_printerror('+', file, line, func, buf);
	return -1;
}

fz_error fz_rethrowimp(const char *file, int line, const char *func, fz_error cause, char *fmt, ...)
{
	char buf[150];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(buf, sizeof buf, fmt, ap);
	va_end(ap);
	buf[sizeof(buf)-1] = 0;
	fz_printerror('|', file, line, func, buf);
	return cause;
}

void fz_catchimp(const char *file, int line, const char *func, fz_error cause, char *fmt, ...)
{
	char buf[150];
	va_list ap;
	va_start(ap, fmt);
	vsnprintf(buf, sizeof buf, fmt, ap);
	va_end(ap);
	buf[sizeof(buf)-1] = 0;
	fz_printerror('\\', file, line, func, buf);
	fz_errorclear = 1;
}

