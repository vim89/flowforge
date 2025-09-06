import com.flowforge.core.contracts._
import com.flowforge.core.contracts.SchemaPolicy._
import com.flowforge.core.contracts.derive.Shape

final case class OutA(id: Long, name: String)
final case class ContractA(id: Long, name: String)

object Good {
  // Compiles: shapes match under Exact
  implicitly[SchemaConforms[OutA, ContractA, Exact]]
}
