package com.a17.assist.ffvn.inject;

interface IA17Input {
    void down(int x, int y, long eventTime);
    void move(int x, int y, long eventTime);
    void up(int x, int y, long eventTime);
    void destroy();
}
