package main

import "fmt"

func add(x int, y string) int {
	return x + y
}

func main() {
	fmt.Println(add(42, 13))
}
