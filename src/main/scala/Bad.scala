import com.flowforge.core.contracts._
import com.flowforge.core.contracts.SchemaPolicy._
import com.flowforge.core.contracts.derive.Shape

final case class OutA(id: Long, name: String)
final case class ContractA(id: Long) // drift: missing 'name'

object Bad {
  // This should not compile under Exact policy
  implicitly[SchemaConforms[OutA, ContractA, Exact]]
}
