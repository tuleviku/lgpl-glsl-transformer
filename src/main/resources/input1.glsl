#version 543
uniform sampler2D Sampler;
in type variable_name1;
in type variable_name2;

out type out_variable;
  
uniform type a_uniform;
  
void main() {
  out_variable = result_of_things + 5;
}
