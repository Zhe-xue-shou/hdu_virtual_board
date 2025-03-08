module light(
        input clk_in,
        input  wire rst,
        input  wire [1:0] SW,
        output reg [15:0] light=16'H8000
    );

    reg [1:0] old_SW=2'b00;

    always @(rst or SW or posedge clk_in) begin
        if(SW==old_SW&&!rst)  begin
            case (SW)
                2'b00: light<={light[0],light[15:1]};
                2'b01: light<={light[14:0],light[15]};
                2'b10: light<={light[14:8],light[15],light[0],light[7:1]};
                2'b11: light<={light[8],light[15:9],light[6:0],light[7]};
            endcase
        end else begin
            case (SW)
                2'b00: light<=16'H8000;
                2'b01: light<=16'H0001;
                2'b10: light<=16'H0180;
                2'b11: light<=16'H8001;
            endcase
        end
        old_SW<=SW;
    end

endmodule
