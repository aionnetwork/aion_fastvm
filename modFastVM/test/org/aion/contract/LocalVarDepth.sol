pragma solidity ^0.4.15;

contract LocalVar {
  event Number(uint);
  // test for function definition
  function test_func(
    address a_1,
    address a_2,
    address a_3,
    address a_4,
    address a_5,
    address a_6,
    address a_7,
    address a_8,
    address a_9,
    address a_10,
    address a_11,
    address a_12,
    address a_13,
    address a_14
  ) returns (uint) {
    uint b_1 = 100;
    uint b_2 = 99;
    uint b_3 = b_2 - b_1;
    return -b_3;
  }
  
  uint[] a = new uint[](10);
  function copyArrayToStorage() returns (uint[]){
    uint[10] memory b = [uint(1),2,3,4,5,6,7,8,9,10];
    a[6] = 99;
    a = b;
    return a;
  }

  //test for assignment expression
  function storeValue() {
    uint a_1 = 0;
    uint a_2 = 0;
    uint a_3 = 0;
    uint a_4 = 0;
    uint a_5 = 0;
    uint a_6 = 0;
    uint a_7 = 0;
    uint a_8 = 0;
    uint a_9 = 0;
    uint a_10 = 0;
    uint a_11 = 0;
    uint a_12 = 0;
    uint a_13 = 0;
    uint a_14 = 0;
    uint a_15 = 0;
    uint a_16 = 0;
    uint a_17 = 0;
    a_1 = 10;
  }
  
  // test for variable declaration
  struct A {
    uint a_1;
    uint a_2;
    uint a_3;
    uint a_4;
    uint a_5;
    uint a_6;
    uint a_7;
    uint a_8;
    uint a_9;
    uint a_10;
    uint a_11;
    uint a_12;
    uint a_13;
    uint a_14;
    uint a_15;
    uint a_16;
    uint a_17;
  }

  // public variable: test the default getter
  A public b;

  uint c = 10;
  A e;
  bytes ids;

  // assignment test
  function do_assign() {
    b.a_1 = 10;
    delete e;
  }

  // dynamic return type
  string name = "aion";
  function check_return() returns (
    string,
    string,
    string,
    string,
    string,
    string,
    string,
    string
  ) {
    return (
      name,
      name,
      name,
      name,
      name,
      name,
      name,
      name
    );
  }

  function small_recursive(uint n) returns(uint) {
    return n == 0? 0:n+small_recursive(n-1);
  }

  function recursive(
    address a_1,
    address a_2,
    address a_3,
    address a_4,
    address a_5,
    address a_6,
    address a_7,
    address a_8,
    address a_9,
    address a_10,
    address a_11,
    address a_12,
    address a_13,
    address a_14,
    uint n
  ) returns (uint) {
    if (n <= 1)
      return n;
    else {
      Number(n);
      return 1+recursive(
        a_1,
        a_2,
        a_3,
        a_4,
        a_5,
        a_6,
        a_7,
        a_8,
        a_9,
        a_10,
        a_11,
        a_12,
        a_13,
        a_14,
        n -1
      );
    }
  }
}
