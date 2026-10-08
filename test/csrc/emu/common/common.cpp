/***************************************************************************************
 * Copyright (c) 2020-2023 Institute of Computing Technology, Chinese Academy of Sciences
 * Copyright (c) 2020-2021 Peng Cheng Laboratory
 *
 * DiffTest is licensed under Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *          http://license.coscl.org.cn/MulanPSL2
 *
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
 * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
 * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
 *
 * See the Mulan PSL v2 for more details.
 ***************************************************************************************/

#include "common.h"

#include <locale.h>
#include <signal.h>
#include <time.h>

int assert_count = 0;
int signal_num = 0;
const char *emu_path = NULL;
static char mybuf[BUFSIZ];
static struct timeval boot = {};
static eprintf_handle_t eprintf_handle = vprintf;

int eprintf(const char *fmt, ...) {
    va_list args;
    int ret;
    va_start(args, fmt);
    ret = (*eprintf_handle)(fmt, args);
    va_end(args);
    return ret;
}

void sig_handler(int signo) {
    if (signal_num != 0)
        exit(0);
    signal_num = signo;
}

uint32_t uptime(void) {
    struct timeval t;
    gettimeofday(&t, NULL);

    int s = t.tv_sec - boot.tv_sec;
    int us = t.tv_usec - boot.tv_usec;
    if (us < 0) {
        s--;
        us += 1000000;
    }

    return s * 1000 + (us + 500) / 1000;
}

void common_init_without_assertion(const char *program_name) {
    // set emu_path
    emu_path = program_name;

    const char *elf_name = strrchr(program_name, '/');
    elf_name = elf_name ? elf_name + 1 : program_name;
    Info("%s compiled at %s, %s\n", elf_name, __DATE__, __TIME__);

    // set buffer for stderr
    setbuf(stderr, mybuf);

    // enable thousands separator for printf()
    setlocale(LC_NUMERIC, "");

    // set up SIGINT handler
    if (signal(SIGINT, sig_handler) == SIG_ERR) {
        Info("\ncan't catch SIGINT\n");
    }

    gettimeofday(&boot, NULL);

    assert_count = -1;
    signal_num = 0;
}
