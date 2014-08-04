package org.http4s

trait Http4s extends EntityDecoderInstances
  with MessageSyntax
  with WritableInstances
  with StatusInstances
  with CharsetRangeInstances
  with QValueFunctions
  with util.CaseInsensitiveStringSyntax
  with util.TaskInstances
  with scalaz.std.AllInstances

object Http4s extends Http4s