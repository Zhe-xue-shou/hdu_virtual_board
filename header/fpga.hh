#ifndef FPGA__HH_
#define FPGA__HH_

#include <thread>
#include <mutex>
#include <atomic>
#include <map>
#include <vector>
#include <string>
#include <nlohmann/json.hpp>
#include <iostream>
#include <sstream>

std::atomic_bool running(true);
std::mutex value_mutex;
std::map<void *, std::vector<std::string>> pins_map;

nlohmann::json old_output_json;

// 置位函数（已内联）
#if 0
void set_bit(void *ptr, int bit_offset, int value) {
    uint8_t *byte_ptr = static_cast<uint8_t *>(ptr); // 将 void* 转换为字节指针

    // 计算目标字节的位置和位的偏移量
    int byte_index = bit_offset / 8;  // 目标字节索引
    int bit_index = bit_offset % 8;  // 目标字节内的位索引

    // 修改对应的位
    if (value) {
        byte_ptr[byte_index] |= (1 << bit_index); // 设置该位为 1
    } else {
        byte_ptr[byte_index] &= ~(1 << bit_index); // 清除该位为 0
    }
}
#endif

void update_signal_from_json(const nlohmann::json &input_json, void *sgl_ptr) {
    const auto &v = pins_map[sgl_ptr];
    uint8_t *byte_ptr = static_cast<uint8_t *>(sgl_ptr);
    int byte_idx = 0;
    int bit_idx = 0;
    for (auto p = v.rbegin();p != v.rend();p++) {
        if (!input_json.contains(*p))
            continue;
        if (input_json[*p] != 0) {
            byte_ptr[byte_idx] |= (1 << bit_idx);
        } else {
            byte_ptr[byte_idx] &= ~(1 << bit_idx);
        }
        ++bit_idx;
        byte_idx += bit_idx / 8;
        bit_idx %= 8;
    }
}

// 需要自己在模块中实现
void update_all_signals_from_json(const nlohmann::json &input_json);

void output_signal_to_json(nlohmann::json &output_json, void *sgl_ptr) {
    auto &v = pins_map[sgl_ptr];
    uint8_t *byte_ptr = static_cast<uint8_t *>(sgl_ptr);
    int byte_idx = 0;
    int bit_idx = 0;
    for (auto p = v.rbegin();p != v.rend();p++) {
        output_json[*p] = (byte_ptr[byte_idx] >> bit_idx) & 1;

        ++bit_idx;
        byte_idx += bit_idx / 8;
        bit_idx %= 8;
    }
}

// 需要自己在模块中实现
void update_all_signals_to_json(nlohmann::json &output_json);

// 线程函数
void listen_for_input() {
    while (running) {
        nlohmann::json input_json;
        try {
            if (std::cin >> input_json) {
                std::lock_guard<std::mutex> lock(value_mutex);
                update_all_signals_from_json(input_json);
            }
        }
        catch (const std::exception &e) {
            if (std::cin.eof()) { // 检测 EOF
                running = false;
                break;
            } else {
                std::cerr << "JSON Parse Error: " << e.what() << std::endl;
            }
        }
    }
}

#endif