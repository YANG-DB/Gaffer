/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.gov.gchq.gaffer.operation.impl.output;

import com.fasterxml.jackson.core.type.TypeReference;
import uk.gov.gchq.gaffer.operation.Operation;
import uk.gov.gchq.gaffer.operation.data.EntitySeed;
import uk.gov.gchq.gaffer.operation.io.InputOutput;
import uk.gov.gchq.gaffer.operation.io.MultiInput;
import uk.gov.gchq.gaffer.operation.serialisation.TypeReferenceImpl;

/**
 * A <code>ToEntitySeeds</code> operation takes in an {@link java.lang.Iterable}
 * of items and converts them into {@link uk.gov.gchq.gaffer.operation.data.EntitySeed}s.
 *
 * @see uk.gov.gchq.gaffer.operation.impl.output.ToEntitySeeds.Builder
 */
public class ToEntitySeeds implements
        Operation,
        InputOutput<Iterable<? extends Object>, Iterable<? extends EntitySeed>>,
        MultiInput<Object> {
    private Iterable<? extends Object> input;

    @Override
    public Iterable<? extends Object> getInput() {
        return input;
    }

    @Override
    public void setInput(final Iterable<? extends Object> input) {
        this.input = input;
    }

    @Override
    public TypeReference<Iterable<? extends EntitySeed>> getOutputTypeReference() {
        return new TypeReferenceImpl.IterableEntitySeed();
    }

    @Override
    public ToEntitySeeds shallowClone() {
        return new ToEntitySeeds.Builder()
                .input(input)
                .build();
    }

    public static final class Builder
            extends BaseBuilder<ToEntitySeeds, Builder>
            implements InputOutput.Builder<ToEntitySeeds, Iterable<? extends Object>, Iterable<? extends EntitySeed>, Builder>,
            MultiInput.Builder<ToEntitySeeds, Object, Builder> {
        public Builder() {
            super(new ToEntitySeeds());
        }
    }
}
