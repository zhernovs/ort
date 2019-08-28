//data class TestModel(
//    val name: String
//) {
//    fun nameString() = "Name: $name"
//
//    expect fun printName()
//}

expect class Foo(bar: String) {
    fun frob()
}

//fun main() {
//    Foo("Hello").frob()
//}
