package com.jeremy.shizukuai;

interface IRemoteAiService {
    String execCommand(String command) = 1;
    String dumpUiHierarchy() = 2;
    void injectTap(int x, int y) = 3;
    void destroy() = 16777114;
}
