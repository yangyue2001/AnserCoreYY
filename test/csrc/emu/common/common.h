#ifndef __COMMON_H
#define __COMMON_H

#include <cassert>

extern bool sim_verbose;

int eprintf(const char *fmt, ...);

#define Info(...)                 \
    do {                          \
        if (sim_verbose) {        \
            eprintf(__VA_ARGS__); \
        }                         \
    } while (0)

#endif

typedef int (*eprintf_handle_t)(const char *fmt, va_list ap);
