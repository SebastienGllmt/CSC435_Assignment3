package main

import "fmt"


func main() {
  var arr[2*3*5*10] int32;
  var foo[2*3*5*10] int32;
  foo[30] = 90;
  arr[0] = 20;
  arr[0] += 20;
  arr[0] += foo[30];
  arr[0] = arr[1] - 20;

	fmt.Println(arr); //, p, v2, v3)
}
