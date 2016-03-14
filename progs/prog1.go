package main

func main() {

  type Days int32
  type Kilograms int32

  var v1 Days
  var v2 Kilograms
  var x = v1;

  v1 = v2 // type mismatch error
  v1 = 3 // OK
  v2 = 3 // OK
}
