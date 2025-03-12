import os
import json
import argparse


def generate_main_cpp(bind_file, module_name, output_file):
    # 读取 bind.json 文件
    with open(bind_file, "r") as f:
        bind_data = json.load(f)

    clk_flag = 0  # 默认关闭clk
    clk_value_name = ""  # clk信号名

    # 解析 inputRows 和 outputRows
    # all_signals = bind_data.get("inputRows", []) + bind_data.get("outputRows", [])
    # input_signals=bind_data.get("inputRows", [])
    # output_signals=bind_data.get("outputRows",[])

    pin_bindings = []
    update_from_json_calls = []
    update_to_json_calls = []

    # 对于输入信号 绑定 读取 更新
    for input_entry in bind_data["inputRows"]:
        input_signal = input_entry["signal"]
        input_pins = input_entry["pins"]

        pin_bindings.append(
            # pin[1]=>pin即可改回原来版本
            f"""    pins_map[&top->{input_signal}] = {{\n        {', '.join(f'"{pin[1]}"' for pin in input_pins)}\n    }};"""
        )
        update_from_json_calls.append(
            f"    update_signal_from_json(input_json, &top->{input_signal});"
        )
        update_to_json_calls.append(
            f"    output_signal_to_json(output_json, &top->{input_signal});"
        )

    # 对于输出信号 绑定 更新
    for output_entry in bind_data["outputRows"]:
        output_signal = output_entry["signal"]
        output_pins = output_entry["pins"]

        pin_bindings.append(
            f"""    pins_map[&top->{output_signal}] = {{\n        {', '.join(f'"{pin[1]}"' for pin in output_pins)}\n    }};"""
        )
        update_to_json_calls.append(
            f"    output_signal_to_json(output_json, &top->{output_signal});"
        )

        if bind_data.get("CLK") and bind_data["CLK"] != "":
            clk_value_name = bind_data["CLK"]
            clk_flag = 1
            continue

    # for entry in all_signals:
    #     signal = entry["signal"]
    #     pins = entry["pins"]

    #     if len(pins) == 1 and pins[0] == "CLK":
    #         clk_value_name = signal
    #         clk_flag = 1
    #         continue

    #     pin_bindings.append(
    #         f"""    pins_map[&top->{signal}] = {{\n        {', '.join(f'"{pin}"' for pin in pins)}\n    }};"""
    #     )
    #     update_from_json_calls.append(
    #         f"    update_signal_from_json(input_json, &top->{signal});"
    #     )
    #     update_to_json_calls.append(
    #         f"    output_signal_to_json(output_json, &top->{signal});"
    #     )
    # 生成 main.cpp 的内容
    main_cpp_content = f"""#include "V{module_name}.h"
#include "verilated.h"
#include <iostream>
#include <mutex>
#include <atomic>
#include <nlohmann/json.hpp>
#include <chrono>
#include "../../header/fpga.hh"
#define COUNT_TIME 5'000

{"#define CLK_FLAG" if clk_flag else ""}

using namespace std;

V{module_name} *top = new V{module_name};

// 创建一个用于绑定信号和引脚的映射
void bind_all_pins() {{
{chr(10).join(pin_bindings)}
}}

// 更新所有信号
void update_all_signals_from_json(const nlohmann::json &input_json) {{
{chr(10).join(update_from_json_calls)}
}}

void update_all_signals_to_json(nlohmann::json &output_json) {{
{chr(10).join(update_to_json_calls)}
}}

int main(int argc, char **argv) {{
    Verilated::commandArgs(argc, argv);
    vluint64_t main_time = 0;

    std::thread input_thread(listen_for_input);

    bind_all_pins();

#ifdef  CLK_FLAG
    int time = 0;
#endif
    while (running && !Verilated::gotFinish()) {{
        {{        
#ifdef CLK_FLAG
            time++;
            if (time == COUNT_TIME) {{
                top->{clk_value_name} ^= 1;
                time = 0;
            }}
#endif
            nlohmann::json output_json;
            std::lock_guard<std::mutex> lock(value_mutex);

            top->eval();

            update_all_signals_to_json(output_json);
            if (output_json != old_output_json) {{
                old_output_json = output_json;
                output_json["Timer"] = main_time++;
                cout << output_json << endl;
            }}
        }}
    }}

    running = false;
    input_thread.join();

    delete top;
    std::cout << "Simulation ended gracefully." << std::endl;
    return 0;
}}
"""
    # 将内容写入指定文件
    with open(output_file, "w") as f:
        f.write(main_cpp_content)
    print(f"Generated {output_file} successfully.")

    # 将内容写入指定文件
    with open(output_file, "w") as f:
        f.write(main_cpp_content)
    print(f"Generated {output_file} successfully.")


def create_workspace(workspace_name, module_name, verilog_file, bind_file):
    # 工作区基础路径
    base_dir = os.path.abspath(workspace_name)

    # 定义目录结构
    dirs = [
        base_dir,
        os.path.join(base_dir, "csrc"),
        os.path.join(base_dir, "vsrc"),
        os.path.join(base_dir, "constr"),  # 新增 constr 目录
    ]

    # 创建目录
    for directory in dirs:
        os.makedirs(directory, exist_ok=True)
        print(f"Created directory: {directory}")

    # 将 Verilog 文件复制到 vsrc 目录
    vsrc_path = os.path.join(base_dir, "vsrc", f"{module_name}.v")
    with open(vsrc_path, "wb") as f:
        with open(verilog_file, "rb") as src:
            f.write(src.read())
    print(f"Copied Verilog file to: {vsrc_path}")

    # 将 bind.json 文件复制到 constr 目录
    constr_path = os.path.join(base_dir, "constr", "bind.json")
    with open(constr_path, "wb") as f:
        with open(bind_file, "rb") as src:
            f.write(src.read())
    print(f"Copied bind.json to: {constr_path}")

    # 生成 main.cpp 文件
    main_cpp_path = os.path.join(base_dir, "csrc", "main.cpp")
    generate_main_cpp(bind_file, module_name, main_cpp_path)

    # 生成 Makefile
    makefile_path = os.path.join(base_dir, "Makefile")
    with open(makefile_path, "w") as f:
        f.write(generate_makefile_content(module_name))
    print(f"Generated Makefile: {makefile_path}")


def generate_makefile_content(topname):
    """
    生成动态 Makefile 内容，TOPNAME 替换为模块名。
    """
    return f"""# 顶层模块名称
TOPNAME = {topname}

# Verilator 编译器配置
VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc -O3 --x-assign fast --x-initial fast --noassert

# 构建目录和输出二进制文件
BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)

# 搜索项目的 Verilog 和 C/C++ 源文件
VSRCS = $(shell find $(abspath ./vsrc) -name "*.v")
# CSRCS = $(shell find $(abspath ./csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
CSRCS = $(abspath ./csrc/main.cpp)
# 默认目标
default: $(BIN)

# 创建构建目录（如果不存在）
$(shell mkdir -p $(BUILD_DIR))

# Verilator 编译规则
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""

# 生成二进制文件的规则
$(BIN): $(VSRCS) $(CSRCS)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $^ \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN))

# 运行生成的二进制文件
run: $(BIN)
	@$^

# 清理构建目录
clean:
	rm -rf $(BUILD_DIR)

.PHONY: default all clean run

"""


def main():
    parser = argparse.ArgumentParser(
        description="Create a Verilator workspace and generate main.cpp."
    )
    parser.add_argument(
        "--workspace-name", required=True, help="Name of the workspace to create."
    )
    parser.add_argument(
        "--verilog-file", required=True, help="Path to the Verilog (.v) file."
    )
    parser.add_argument(
        "--bind-json", required=True, help="Path to the bind.json file."
    )

    args = parser.parse_args()

    # 获取顶层模块名（Verilog 文件名去掉扩展名）
    module_name = os.path.basename(args.verilog_file).rsplit(".", 1)[0]

    try:
        create_workspace(
            args.workspace_name, module_name, args.verilog_file, args.bind_json
        )
    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    main()
