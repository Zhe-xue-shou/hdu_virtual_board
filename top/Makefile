# 顶层模块名称
TOPNAME = top

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
