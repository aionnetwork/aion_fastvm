/*
    This file is part of solidity.

    solidity is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    solidity is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with solidity.  If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * @author Christian <c@ethdev.com>
 * @date 2014
 * Solidity data types
 */

#include <libsolidity/ast/Types.h>

#include <libsolidity/ast/AST.h>

#include <libdevcore/CommonIO.h>
#include <libdevcore/CommonData.h>
#include <libdevcore/SHA3.h>
#include <libdevcore/UTF8.h>

#include <boost/algorithm/string/join.hpp>
#include <boost/algorithm/string/replace.hpp>
#include <boost/algorithm/string/predicate.hpp>
#include <boost/range/adaptor/reversed.hpp>
#include <boost/range/adaptor/sliced.hpp>
#include <boost/range/adaptor/transformed.hpp>

#include <limits>

using namespace std;
using namespace dev;
using namespace dev::solidity;

void StorageOffsets::computeOffsets(TypePointers const& _types)
{
	bigint slotOffset = 0;
	unsigned byteOffset = 0;
	map<size_t, pair<u128, unsigned>> offsets;
	for (size_t i = 0; i < _types.size(); ++i)
	{
		TypePointer const& type = _types[i];
		if (!type->canBeStored())
			continue;
		if (byteOffset + type->storageBytes() > 16 && byteOffset > 0)
		{
			// would overflow, go to next slot
			++slotOffset;
			byteOffset = 0;
		}
		if (slotOffset >= bigint(1) << 128)
			BOOST_THROW_EXCEPTION(Error(Error::Type::TypeError) << errinfo_comment("Object too large for storage."));
		offsets[i] = make_pair(u128(slotOffset), byteOffset);
		solAssert(type->storageSize() >= 1, "Invalid storage size.");
		if (type->storageSize() == 1 && byteOffset + type->storageBytes() <= 16)
			byteOffset += type->storageBytes();
		else
		{
			slotOffset += type->storageSize();
			byteOffset = 0;
		}
	}
	if (byteOffset > 0)
		++slotOffset;
	if (slotOffset >= bigint(1) << 128)
		BOOST_THROW_EXCEPTION(Error(Error::Type::TypeError) << errinfo_comment("Object too large for storage."));
	m_storageSize = u128(slotOffset);
	swap(m_offsets, offsets);
}

pair<u128, unsigned> const* StorageOffsets::offset(size_t _index) const
{
	if (m_offsets.count(_index))
		return &m_offsets.at(_index);
	else
		return nullptr;
}

MemberList& MemberList::operator=(MemberList&& _other)
{
	assert(&_other != this);

	m_memberTypes = move(_other.m_memberTypes);
	m_storageOffsets = move(_other.m_storageOffsets);
	return *this;
}

void MemberList::combine(MemberList const & _other)
{
	m_memberTypes += _other.m_memberTypes;
}

pair<u128, unsigned> const* MemberList::memberStorageOffset(string const& _name) const
{
	if (!m_storageOffsets)
	{
		TypePointers memberTypes;
		memberTypes.reserve(m_memberTypes.size());
		for (auto const& member: m_memberTypes)
			memberTypes.push_back(member.type);
		m_storageOffsets.reset(new StorageOffsets());
		m_storageOffsets->computeOffsets(memberTypes);
	}
	for (size_t index = 0; index < m_memberTypes.size(); ++index)
		if (m_memberTypes[index].name == _name)
			return m_storageOffsets->offset(index);
	return nullptr;
}

u128 const& MemberList::storageSize() const
{
	// trigger lazy computation
	memberStorageOffset("");
	return m_storageOffsets->storageSize();
}

/// Helper functions for type identifier
namespace
{

string parenthesizeIdentifier(string const& _internal)
{
	return "$_" + _internal + "_$";
}

template <class Range>
string identifierList(Range const&& _list)
{
	return parenthesizeIdentifier(boost::algorithm::join(_list, "_$_"));
}

string identifier(TypePointer const& _type)
{
	return _type ? _type->identifier() : "";
}

string identifierList(vector<TypePointer> const& _list)
{
	return identifierList(_list | boost::adaptors::transformed(identifier));
}

string identifierList(TypePointer const& _type)
{
	return parenthesizeIdentifier(identifier(_type));
}

string identifierList(TypePointer const& _type1, TypePointer const& _type2)
{
	TypePointers list;
	list.push_back(_type1);
	list.push_back(_type2);
	return identifierList(list);
}

string parenthesizeUserIdentifier(string const& _internal)
{
	return parenthesizeIdentifier(boost::algorithm::replace_all_copy(_internal, "$", "$$$"));
}

}

TypePointer Type::fromElementaryTypeName(ElementaryTypeNameToken const& _type)
{
	solAssert(Token::isElementaryTypeName(_type.token()),
		"Expected an elementary type name but got " + _type.toString()
	);

	Token::Value token = _type.token();
	unsigned m = _type.firstNumber();
	unsigned n = _type.secondNumber();

	switch (token)
	{
	case Token::IntM:
		return make_shared<IntegerType>(m, IntegerType::Modifier::Signed);
	case Token::UIntM:
		return make_shared<IntegerType>(m, IntegerType::Modifier::Unsigned);
	case Token::BytesM:
		return make_shared<FixedBytesType>(m);
	case Token::FixedMxN:
		return make_shared<FixedPointType>(m, n, FixedPointType::Modifier::Signed);
	case Token::UFixedMxN:
		return make_shared<FixedPointType>(m, n, FixedPointType::Modifier::Unsigned);
	case Token::Int:
		return make_shared<IntegerType>(128, IntegerType::Modifier::Signed);
	case Token::UInt:
		return make_shared<IntegerType>(128, IntegerType::Modifier::Unsigned);
	case Token::Fixed:
		return make_shared<FixedPointType>(128, 19, FixedPointType::Modifier::Signed);
	case Token::UFixed:
		return make_shared<FixedPointType>(128, 19, FixedPointType::Modifier::Unsigned);
	case Token::Byte:
		return make_shared<FixedBytesType>(1);
	case Token::Address:
		return make_shared<IntegerType>(0, IntegerType::Modifier::Address);
	case Token::Bool:
		return make_shared<BoolType>();
	case Token::Bytes:
		return make_shared<ArrayType>(DataLocation::Storage);
	case Token::String:
		return make_shared<ArrayType>(DataLocation::Storage, true);
	//no types found
	default:
		solAssert(
			false,
			"Unable to convert elementary typename " + _type.toString() + " to type."
		);
	}
}

TypePointer Type::fromElementaryTypeName(string const& _name)
{
	unsigned short firstNum;
	unsigned short secondNum;
	Token::Value token;
	tie(token, firstNum, secondNum) = Token::fromIdentifierOrKeyword(_name);
 	return fromElementaryTypeName(ElementaryTypeNameToken(token, firstNum, secondNum));
}

TypePointer Type::forLiteral(Literal const& _literal)
{
	switch (_literal.token())
	{
	case Token::TrueLiteral:
	case Token::FalseLiteral:
		return make_shared<BoolType>();
	case Token::Number:
	{
		tuple<bool, rational> validLiteral = RationalNumberType::isValidLiteral(_literal);
		if (get<0>(validLiteral) == true)
			return make_shared<RationalNumberType>(get<1>(validLiteral));
		else
			return TypePointer();
	}
	case Token::StringLiteral:
		return make_shared<StringLiteralType>(_literal);
	default:
		return TypePointer();
	}
}

TypePointer Type::commonType(TypePointer const& _a, TypePointer const& _b)
{
	if (!_a || !_b)
		return TypePointer();
	else if (_a->mobileType() && _b->isImplicitlyConvertibleTo(*_a->mobileType()))
		return _a->mobileType();
	else if (_b->mobileType() && _a->isImplicitlyConvertibleTo(*_b->mobileType()))
		return _b->mobileType();
	else
		return TypePointer();
}

MemberList const& Type::members(ContractDefinition const* _currentScope) const
{
	if (!m_members[_currentScope])
	{
		MemberList::MemberMap members = nativeMembers(_currentScope);
		if (_currentScope)
			members += boundFunctions(*this, *_currentScope);
		m_members[_currentScope] = unique_ptr<MemberList>(new MemberList(move(members)));
	}
	return *m_members[_currentScope];
}

MemberList::MemberMap Type::boundFunctions(Type const& _type, ContractDefinition const& _scope)
{
	// Normalise data location of type.
	TypePointer type = ReferenceType::copyForLocationIfReference(DataLocation::Storage, _type.shared_from_this());
	set<Declaration const*> seenFunctions;
	MemberList::MemberMap members;
	for (ContractDefinition const* contract: _scope.annotation().linearizedBaseContracts)
		for (UsingForDirective const* ufd: contract->usingForDirectives())
		{
			if (ufd->typeName() && *type != *ReferenceType::copyForLocationIfReference(
				DataLocation::Storage,
				ufd->typeName()->annotation().type
			))
				continue;
			auto const& library = dynamic_cast<ContractDefinition const&>(
				*ufd->libraryName().annotation().referencedDeclaration
			);
			for (FunctionDefinition const* function: library.definedFunctions())
			{
				if (!function->isVisibleInDerivedContracts() || seenFunctions.count(function))
					continue;
				seenFunctions.insert(function);
				FunctionType funType(*function, false);
				if (auto fun = funType.asMemberFunction(true, true))
					if (_type.isImplicitlyConvertibleTo(*fun->selfType()))
						members.push_back(MemberList::Member(function->name(), fun, function));
			}
		}
	return members;
}

bool isValidShiftAndAmountType(Token::Value _operator, Type const& _shiftAmountType)
{
	// Disable >>> here.
	if (_operator == Token::SHR)
		return false;
	else if (IntegerType const* otherInt = dynamic_cast<decltype(otherInt)>(&_shiftAmountType))
		return !otherInt->isAddress();
	else if (RationalNumberType const* otherRat = dynamic_cast<decltype(otherRat)>(&_shiftAmountType))
		return otherRat->integerType() && !otherRat->integerType()->isSigned();
	else
		return false;
}

IntegerType::IntegerType(int _bits, IntegerType::Modifier _modifier):
	m_bits(_bits), m_modifier(_modifier)
{
	if (isAddress())
		m_bits = 256;
	solAssert(
		m_bits > 0 && m_bits <= 256 && m_bits % 8 == 0,
		"Invalid bit number for integer type: " + dev::toString(_bits)
				);
}

string IntegerType::identifier() const
{
	if (isAddress())
		return "t_address";
	else
		return "t_" + string(isSigned() ? "" : "u") + "int" + std::to_string(numBits());
}

bool IntegerType::isImplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (_convertTo.category() == category())
	{
		IntegerType const& convertTo = dynamic_cast<IntegerType const&>(_convertTo);
		if (convertTo.m_bits < m_bits)
			return false;
		if (isAddress())
			return convertTo.isAddress();
		else if (isSigned())
			return convertTo.isSigned();
		else
			return !convertTo.isSigned() || convertTo.m_bits > m_bits;
	}
	else if (_convertTo.category() == Category::FixedPoint)
	{
		FixedPointType const& convertTo = dynamic_cast<FixedPointType const&>(_convertTo);

		if (isAddress())
			return false;
		else
			return maxValue() <= convertTo.maxIntegerValue() && minValue() >= convertTo.minIntegerValue();
	}
	else
		return false;
}

bool IntegerType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	return _convertTo.category() == category() ||
		_convertTo.category() == Category::Contract ||
		_convertTo.category() == Category::Enum ||
		_convertTo.category() == Category::FixedBytes ||
		_convertTo.category() == Category::FixedPoint;
}

TypePointer IntegerType::unaryOperatorResult(Token::Value _operator) const
{
	// "delete" is ok for all integer types
	if (_operator == Token::Delete)
		return make_shared<TupleType>();
	// no further unary operators for addresses
	else if (isAddress())
		return TypePointer();
	// for non-address integers, we allow +, -, ++ and --
	else if (_operator == Token::Add || _operator == Token::Sub ||
			_operator == Token::Inc || _operator == Token::Dec ||
			_operator == Token::BitNot)
		return shared_from_this();
	else
		return TypePointer();
}

bool IntegerType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	IntegerType const& other = dynamic_cast<IntegerType const&>(_other);
	return other.m_bits == m_bits && other.m_modifier == m_modifier;
}

string IntegerType::toString(bool) const
{
	if (isAddress())
		return "address";
	string prefix = isSigned() ? "int" : "uint";
	return prefix + dev::toString(m_bits);
}

u256 IntegerType::literalValue(Literal const* _literal) const
{
	solAssert(m_modifier == Modifier::Address, "");
	solAssert(_literal, "");
	solAssert(_literal->value().substr(0, 2) == "0x", "");
	return u256(_literal->value());
}

bigint IntegerType::minValue() const
{
	if (isSigned())
		return -(bigint(1) << (m_bits - 1));
	else
		return bigint(0);
}

bigint IntegerType::maxValue() const
{
	if (isSigned())
		return (bigint(1) << (m_bits - 1)) - 1;
	else
		return (bigint(1) << m_bits) - 1;
}

TypePointer IntegerType::binaryOperatorResult(Token::Value _operator, TypePointer const& _other) const
{
	if (
		_other->category() != Category::RationalNumber &&
		_other->category() != Category::FixedPoint &&
		_other->category() != category()
	)
		return TypePointer();
	if (Token::isShiftOp(_operator))
	{
		// Shifts are not symmetric with respect to the type
		if (isAddress())
			return TypePointer();
		if (isValidShiftAndAmountType(_operator, *_other))
			return shared_from_this();
		else
			return TypePointer();
	}

	auto commonType = Type::commonType(shared_from_this(), _other); //might be a integer or fixed point
	if (!commonType)
		return TypePointer();

	// All integer types can be compared
	if (Token::isCompareOp(_operator))
		return commonType;
	if (Token::isBooleanOp(_operator))
		return TypePointer();
	if (auto intType = dynamic_pointer_cast<IntegerType const>(commonType))
	{
		// Nothing else can be done with addresses
		if (intType->isAddress())
			return TypePointer();
		// Signed EXP is not allowed
		if (Token::Exp == _operator && intType->isSigned())
			return TypePointer();
	}
	else if (auto fixType = dynamic_pointer_cast<FixedPointType const>(commonType))
		if (Token::Exp == _operator)
			return TypePointer();
	return commonType;
}

MemberList::MemberMap IntegerType::nativeMembers(ContractDefinition const*) const
{
	if (isAddress())
		return {
			{"balance", make_shared<IntegerType >(128)},
			{"call", make_shared<FunctionType>(strings(), strings{"bool"}, FunctionType::Kind::BareCall, true, false, true)},
			{"callcode", make_shared<FunctionType>(strings(), strings{"bool"}, FunctionType::Kind::BareCallCode, true, false, true)},
			{"delegatecall", make_shared<FunctionType>(strings(), strings{"bool"}, FunctionType::Kind::BareDelegateCall, true)},
			{"send", make_shared<FunctionType>(strings{"uint"}, strings{"bool"}, FunctionType::Kind::Send)},
			{"transfer", make_shared<FunctionType>(strings{"uint"}, strings(), FunctionType::Kind::Transfer)}
		};
	else
		return MemberList::MemberMap();
}

FixedPointType::FixedPointType(int _totalBits, int _fractionalDigits, FixedPointType::Modifier _modifier):
	m_totalBits(_totalBits), m_fractionalDigits(_fractionalDigits), m_modifier(_modifier)
{
	solAssert(
		8 <= m_totalBits && m_totalBits <= 256 && m_totalBits % 8 == 0 &&
		0 <= m_fractionalDigits && m_fractionalDigits <= 80, 
		"Invalid bit number(s) for fixed type: " + 
		dev::toString(_totalBits) + "x" + dev::toString(_fractionalDigits)
	);
}

string FixedPointType::identifier() const
{
	return "t_" + string(isSigned() ? "" : "u") + "fixed" + std::to_string(m_totalBits) + "x" + std::to_string(m_fractionalDigits);
}

bool FixedPointType::isImplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (_convertTo.category() == category())
	{
		FixedPointType const& convertTo = dynamic_cast<FixedPointType const&>(_convertTo);
		if (convertTo.numBits() < m_totalBits || convertTo.fractionalDigits() < m_fractionalDigits)
			return false;
		else
			return convertTo.maxIntegerValue() >= maxIntegerValue() && convertTo.minIntegerValue() <= minIntegerValue();
	}
	return false;
}

bool FixedPointType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	return _convertTo.category() == category() ||
		_convertTo.category() == Category::Integer ||
		_convertTo.category() == Category::FixedBytes;
}

TypePointer FixedPointType::unaryOperatorResult(Token::Value _operator) const
{
	// "delete" is ok for all fixed types
	if (_operator == Token::Delete)
		return make_shared<TupleType>();
	// for fixed, we allow +, -, ++ and --
	else if (
		_operator == Token::Add || 
		_operator == Token::Sub ||
		_operator == Token::Inc || 
		_operator == Token::Dec
	)
		return shared_from_this();
	else
		return TypePointer();
}

bool FixedPointType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	FixedPointType const& other = dynamic_cast<FixedPointType const&>(_other);
	return other.m_totalBits == m_totalBits && other.m_fractionalDigits == m_fractionalDigits && other.m_modifier == m_modifier;
}

string FixedPointType::toString(bool) const
{
	string prefix = isSigned() ? "fixed" : "ufixed";
	return prefix + dev::toString(m_totalBits) + "x" + dev::toString(m_fractionalDigits);
}

bigint FixedPointType::maxIntegerValue() const
{
	bigint maxValue = (bigint(1) << (m_totalBits - (isSigned() ? 1 : 0))) - 1;
	return maxValue / pow(bigint(10), m_fractionalDigits);
}

bigint FixedPointType::minIntegerValue() const
{
	if (isSigned())
	{
		bigint minValue = -(bigint(1) << (m_totalBits - (isSigned() ? 1 : 0)));
		return minValue / pow(bigint(10), m_fractionalDigits);
	}
	else
		return bigint(0);
}

TypePointer FixedPointType::binaryOperatorResult(Token::Value _operator, TypePointer const& _other) const
{
	if (
		_other->category() != Category::RationalNumber &&
		_other->category() != category() &&
		_other->category() != Category::Integer
	)
		return TypePointer();
	auto commonType = Type::commonType(shared_from_this(), _other); //might be fixed point or integer

	if (!commonType)
		return TypePointer();

	// All fixed types can be compared
	if (Token::isCompareOp(_operator))
		return commonType;
	if (Token::isBitOp(_operator) || Token::isBooleanOp(_operator))
		return TypePointer();
	if (auto fixType = dynamic_pointer_cast<FixedPointType const>(commonType))
	{
		if (Token::Exp == _operator)
			return TypePointer();
	}
	else if (auto intType = dynamic_pointer_cast<IntegerType const>(commonType))
		if (intType->isAddress())
			return TypePointer();
	return commonType;
}

tuple<bool, rational> RationalNumberType::parseRational(string const& _value)
{
	rational value;
	try
	{
		auto radixPoint = find(_value.begin(), _value.end(), '.');

		if (radixPoint != _value.end())
		{
			if (
				!all_of(radixPoint + 1, _value.end(), ::isdigit) ||
				!all_of(_value.begin(), radixPoint, ::isdigit)
			)
				return make_tuple(false, rational(0));

			// Only decimal notation allowed here, leading zeros would switch to octal.
			auto fractionalBegin = find_if_not(
				radixPoint + 1,
				_value.end(),
				[](char const& a) { return a == '0'; }
			);

			rational numerator;
			rational denominator(1);

			denominator = bigint(string(fractionalBegin, _value.end()));
			denominator /= boost::multiprecision::pow(
				bigint(10),
				distance(radixPoint + 1, _value.end())
			);
			numerator = bigint(string(_value.begin(), radixPoint));
			value = numerator + denominator;
		}
		else
			value = bigint(_value);
		return make_tuple(true, value);
	}
	catch (...)
	{
		return make_tuple(false, rational(0));
	}
}

tuple<bool, rational> RationalNumberType::isValidLiteral(Literal const& _literal)
{
	rational value;
	try
	{
		auto expPoint = find(_literal.value().begin(), _literal.value().end(), 'e');
		if (expPoint == _literal.value().end())
			expPoint = find(_literal.value().begin(), _literal.value().end(), 'E');

		if (boost::starts_with(_literal.value(), "0x"))
		{
			// process as hex
			value = bigint(_literal.value());
		}
		else if (expPoint != _literal.value().end())
		{
			// parse the exponent
			bigint exp = bigint(string(expPoint + 1, _literal.value().end()));

			if (exp > numeric_limits<int32_t>::max() || exp < numeric_limits<int32_t>::min())
				return make_tuple(false, rational(0));

			// parse the base
			tuple<bool, rational> base = parseRational(string(_literal.value().begin(), expPoint));
			if (!get<0>(base))
				return make_tuple(false, rational(0));
			value = get<1>(base);

			if (exp < 0)
			{
				exp *= -1;
				value /= boost::multiprecision::pow(
					bigint(10),
					exp.convert_to<int32_t>()
				);
			}
			else
				value *= boost::multiprecision::pow(
					bigint(10),
					exp.convert_to<int32_t>()
				);
		}
		else
		{
			// parse as rational number
			tuple<bool, rational> tmp = parseRational(_literal.value());
			if (!get<0>(tmp))
				return tmp;
			value = get<1>(tmp);
		}
	}
	catch (...)
	{
		return make_tuple(false, rational(0));
	}
	switch (_literal.subDenomination())
	{
		case Literal::SubDenomination::None:
		case Literal::SubDenomination::Wei:
		case Literal::SubDenomination::Second:
			break;
		case Literal::SubDenomination::Szabo:
			value *= bigint("1000000000000");
			break;
		case Literal::SubDenomination::Finney:
			value *= bigint("1000000000000000");
			break;
		case Literal::SubDenomination::Ether:
			value *= bigint("1000000000000000000");
			break;
		case Literal::SubDenomination::Minute:
			value *= bigint("60");
			break;
		case Literal::SubDenomination::Hour:
			value *= bigint("3600");
			break;
		case Literal::SubDenomination::Day:
			value *= bigint("86400");
			break;
		case Literal::SubDenomination::Week:
			value *= bigint("604800");
			break;
		case Literal::SubDenomination::Year:
			value *= bigint("31536000");
			break;
	}


	return make_tuple(true, value);
}

bool RationalNumberType::isImplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (_convertTo.category() == Category::Integer)
	{
		auto targetType = dynamic_cast<IntegerType const*>(&_convertTo);
		if (m_value == rational(0))
			return true;
		if (isFractional())
			return false;
		int forSignBit = (targetType->isSigned() ? 1 : 0);
		if (m_value > rational(0))
		{
			if (m_value.numerator() <= (u256(-1) >> (256 - targetType->numBits() + forSignBit)))
				return true;
		}
		else if (targetType->isSigned() && -m_value.numerator() <= (u256(1) << (targetType->numBits() - forSignBit)))
			return true;
		return false;
	}
	else if (_convertTo.category() == Category::FixedPoint)
	{
		if (auto fixed = fixedPointType())
			return fixed->isImplicitlyConvertibleTo(_convertTo);
		else
			return false;
	}
	else if (_convertTo.category() == Category::FixedBytes)
	{
		FixedBytesType const& fixedBytes = dynamic_cast<FixedBytesType const&>(_convertTo);
		if (!isFractional())
		{
			if (integerType())
				return fixedBytes.numBytes() * 8 >= integerType()->numBits();
			return false;
		}
		else
			return false;
	}
	return false;
}

bool RationalNumberType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	TypePointer mobType = mobileType();
	return mobType && mobType->isExplicitlyConvertibleTo(_convertTo);
}

TypePointer RationalNumberType::unaryOperatorResult(Token::Value _operator) const
{
	rational value;
	switch (_operator)
	{
	case Token::BitNot:
		if (isFractional())
			return TypePointer();
		value = ~m_value.numerator();
		break;
	case Token::Add:
		value = +(m_value);
		break;
	case Token::Sub:
		value = -(m_value);
		break;
	case Token::After:
		return shared_from_this();
	default:
		return TypePointer();
	}
	return make_shared<RationalNumberType>(value);
}

TypePointer RationalNumberType::binaryOperatorResult(Token::Value _operator, TypePointer const& _other) const
{
	if (_other->category() == Category::Integer || _other->category() == Category::FixedPoint)
	{
		auto mobile = mobileType();
		if (!mobile)
			return TypePointer();
		return mobile->binaryOperatorResult(_operator, _other);
	}
	else if (_other->category() != category())
		return TypePointer();

	RationalNumberType const& other = dynamic_cast<RationalNumberType const&>(*_other);
	if (Token::isCompareOp(_operator))
	{
		// Since we do not have a "BoolConstantType", we have to do the acutal comparison
		// at runtime and convert to mobile typse first. Such a comparison is not a very common
		// use-case and will be optimized away.
		TypePointer thisMobile = mobileType();
		TypePointer otherMobile = other.mobileType();
		if (!thisMobile || !otherMobile)
			return TypePointer();
		return thisMobile->binaryOperatorResult(_operator, otherMobile);
	}
	else
	{
		rational value;
		bool fractional = isFractional() || other.isFractional();
		switch (_operator)
		{
		//bit operations will only be enabled for integers and fixed types that resemble integers
		case Token::BitOr:
			if (fractional)
				return TypePointer();
			value = m_value.numerator() | other.m_value.numerator();
			break;
		case Token::BitXor:
			if (fractional)
				return TypePointer();
			value = m_value.numerator() ^ other.m_value.numerator();
			break;
		case Token::BitAnd:
			if (fractional)
				return TypePointer();
			value = m_value.numerator() & other.m_value.numerator();
			break;
		case Token::Add:
			value = m_value + other.m_value;
			break;
		case Token::Sub:
			value = m_value - other.m_value;
			break;
		case Token::Mul:
			value = m_value * other.m_value;
			break;
		case Token::Div:
			if (other.m_value == rational(0))
				return TypePointer();
			else
				value = m_value / other.m_value;
			break;
		case Token::Mod:
			if (other.m_value == rational(0))
				return TypePointer();
			else if (fractional)
			{
				rational tempValue = m_value / other.m_value;
				value = m_value - (tempValue.numerator() / tempValue.denominator()) * other.m_value;
			}
			else
				value = m_value.numerator() % other.m_value.numerator();
			break;	
		case Token::Exp:
		{
			using boost::multiprecision::pow;
			if (other.isFractional())
				return TypePointer();
			else if (abs(other.m_value) > numeric_limits<uint32_t>::max())
				return TypePointer(); // This will need too much memory to represent.
			uint32_t exponent = abs(other.m_value).numerator().convert_to<uint32_t>();
			bigint numerator = pow(m_value.numerator(), exponent);
			bigint denominator = pow(m_value.denominator(), exponent);
			if (other.m_value >= 0)
				value = rational(numerator, denominator);
			else
				// invert
				value = rational(denominator, numerator);
			break;
		}
		case Token::SHL:
		{
			using boost::multiprecision::pow;
			if (fractional)
				return TypePointer();
			else if (other.m_value < 0)
				return TypePointer();
			else if (other.m_value > numeric_limits<uint32_t>::max())
				return TypePointer();
			uint32_t exponent = other.m_value.numerator().convert_to<uint32_t>();
			value = m_value.numerator() * pow(bigint(2), exponent);
			break;
		}
		// NOTE: we're using >> (SAR) to denote right shifting. The type of the LValue
		//       determines the resulting type and the type of shift (SAR or SHR).
		case Token::SAR:
		{
			using boost::multiprecision::pow;
			if (fractional)
				return TypePointer();
			else if (other.m_value < 0)
				return TypePointer();
			else if (other.m_value > numeric_limits<uint32_t>::max())
				return TypePointer();
			uint32_t exponent = other.m_value.numerator().convert_to<uint32_t>();
			value = rational(m_value.numerator() / pow(bigint(2), exponent), 1);
			break;
		}
		default:
			return TypePointer();
		}
		return make_shared<RationalNumberType>(value);
	}
}

string RationalNumberType::identifier() const
{
	return "t_rational_" + m_value.numerator().str() + "_by_" + m_value.denominator().str();
}

bool RationalNumberType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	RationalNumberType const& other = dynamic_cast<RationalNumberType const&>(_other);
	return m_value == other.m_value;
}

string RationalNumberType::toString(bool) const
{
	if (!isFractional())
		return "int_const " + m_value.numerator().str();
	return "rational_const " + m_value.numerator().str() + '/' + m_value.denominator().str();
}

u256 RationalNumberType::literalValue(Literal const*) const
{
	// We ignore the literal and hope that the type was correctly determined to represent
	// its value.

	u256 value;
	bigint shiftedValue; 

	if (!isFractional())
		shiftedValue = m_value.numerator();
	else
	{
		auto fixed = fixedPointType();
		solAssert(fixed, "");
		int fractionalDigits = fixed->fractionalDigits();
		shiftedValue = (m_value.numerator() / m_value.denominator()) * pow(bigint(10), fractionalDigits);
	}

	// we ignore the literal and hope that the type was correctly determined
	solAssert(shiftedValue <= u256(-1), "Integer constant too large.");
	solAssert(shiftedValue >= -(bigint(1) << 255), "Number constant too small.");

	if (m_value >= rational(0))
		value = u256(shiftedValue);
	else {
		solAssert(shiftedValue >= -(bigint(1) << 128), "Negative number constant too small.");

		value = s2u128(s128(shiftedValue));
	}
	return value;
}

TypePointer RationalNumberType::mobileType() const
{
	if (!isFractional())
		return integerType();
	else
		return fixedPointType();
}

shared_ptr<IntegerType const> RationalNumberType::integerType() const
{
	solAssert(!isFractional(), "integerType() called for fractional number.");
	bigint value = m_value.numerator();
	bool negative = (value < 0);
	if (negative) // convert to positive number of same bit requirements
		value = ((0 - value) - 1) << 1;
	if (value > u256(-1))
		return shared_ptr<IntegerType const>();
	else
		return make_shared<IntegerType>(
			max(bytesRequired(value), 1u) * 8,
			negative ? IntegerType::Modifier::Signed : IntegerType::Modifier::Unsigned
		);
}

shared_ptr<FixedPointType const> RationalNumberType::fixedPointType() const
{
	bool negative = (m_value < 0);
	unsigned fractionalDigits = 0;
	rational value = abs(m_value); // We care about the sign later.
	rational maxValue = negative ? 
		rational(bigint(1) << 255, 1):
		rational((bigint(1) << 256) - 1, 1);

	while (value * 10 <= maxValue && value.denominator() != 1 && fractionalDigits < 80)
	{
		value *= 10;
		fractionalDigits++;
	}
	
	if (value > maxValue)
		return shared_ptr<FixedPointType const>();
	// This means we round towards zero for positive and negative values.
	bigint v = value.numerator() / value.denominator();
	if (negative)
		// modify value to satisfy bit requirements for negative numbers:
		// add one bit for sign and decrement because negative numbers can be larger
		v = (v - 1) << 1;

	if (v > u256(-1))
		return shared_ptr<FixedPointType const>();

	unsigned totalBits = max(bytesRequired(v), 1u) * 8;
	solAssert(totalBits <= 256, "");

	return make_shared<FixedPointType>(
		totalBits, fractionalDigits,
		negative ? FixedPointType::Modifier::Signed : FixedPointType::Modifier::Unsigned
	);
}

StringLiteralType::StringLiteralType(Literal const& _literal):
	m_value(_literal.value())
{
}

bool StringLiteralType::isImplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (auto fixedBytes = dynamic_cast<FixedBytesType const*>(&_convertTo))
		return size_t(fixedBytes->numBytes()) >= m_value.size();
	else if (auto arrayType = dynamic_cast<ArrayType const*>(&_convertTo))
		return
			arrayType->isByteArray() &&
			!(arrayType->dataStoredIn(DataLocation::Storage) && arrayType->isPointer()) &&
			!(arrayType->isString() && !isValidUTF8());
	else
		return false;
}

string StringLiteralType::identifier() const
{
	// Since we have to return a valid identifier and the string itself may contain
	// anything, we hash it.
	return "t_stringliteral_" + toHex(keccak256(m_value).asBytes());
}

bool StringLiteralType::operator==(const Type& _other) const
{
	if (_other.category() != category())
		return false;
	return m_value == dynamic_cast<StringLiteralType const&>(_other).m_value;
}

std::string StringLiteralType::toString(bool) const
{
	size_t invalidSequence;

	if (!dev::validateUTF8(m_value, invalidSequence))
		return "literal_string (contains invalid UTF-8 sequence at position " + dev::toString(invalidSequence) + ")";

	return "literal_string \"" + m_value + "\"";
}

TypePointer StringLiteralType::mobileType() const
{
	return make_shared<ArrayType>(DataLocation::Memory, true);
}

bool StringLiteralType::isValidUTF8() const
{
	return dev::validateUTF8(m_value);
}

shared_ptr<FixedBytesType> FixedBytesType::smallestTypeForLiteral(string const& _literal)
{
	if (_literal.length() <= 32)
		return make_shared<FixedBytesType>(_literal.length());
	return shared_ptr<FixedBytesType>();
}

FixedBytesType::FixedBytesType(int _bytes): m_bytes(_bytes)
{
	solAssert(m_bytes >= 0 && m_bytes <= 32,
			  "Invalid byte number for fixed bytes type: " + dev::toString(m_bytes));
}

bool FixedBytesType::isImplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (_convertTo.category() != category())
		return false;
	FixedBytesType const& convertTo = dynamic_cast<FixedBytesType const&>(_convertTo);
	return convertTo.m_bytes >= m_bytes;
}

bool FixedBytesType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	return _convertTo.category() == Category::Integer ||
		_convertTo.category() == Category::FixedPoint ||
		_convertTo.category() == Category::Contract ||
		_convertTo.category() == category();
}

TypePointer FixedBytesType::unaryOperatorResult(Token::Value _operator) const
{
	// "delete" and "~" is okay for FixedBytesType
	if (_operator == Token::Delete)
		return make_shared<TupleType>();
	else if (_operator == Token::BitNot)
		return shared_from_this();

	return TypePointer();
}

TypePointer FixedBytesType::binaryOperatorResult(Token::Value _operator, TypePointer const& _other) const
{
	if (Token::isShiftOp(_operator))
	{
		if (isValidShiftAndAmountType(_operator, *_other))
			return shared_from_this();
		else
			return TypePointer();
	}

	auto commonType = dynamic_pointer_cast<FixedBytesType const>(Type::commonType(shared_from_this(), _other));
	if (!commonType)
		return TypePointer();

	// FixedBytes can be compared and have bitwise operators applied to them
	if (Token::isCompareOp(_operator) || Token::isBitOp(_operator))
		return commonType;

	return TypePointer();
}

MemberList::MemberMap FixedBytesType::nativeMembers(const ContractDefinition*) const
{
	return MemberList::MemberMap{MemberList::Member{"length", make_shared<IntegerType>(8)}};
}

string FixedBytesType::identifier() const
{
	return "t_bytes" + std::to_string(m_bytes);
}

bool FixedBytesType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	FixedBytesType const& other = dynamic_cast<FixedBytesType const&>(_other);
	return other.m_bytes == m_bytes;
}

u256 BoolType::literalValue(Literal const* _literal) const
{
	solAssert(_literal, "");
	if (_literal->token() == Token::TrueLiteral)
		return u256(1);
	else if (_literal->token() == Token::FalseLiteral)
		return u256(0);
	else
		solAssert(false, "Bool type constructed from non-boolean literal.");
}

TypePointer BoolType::unaryOperatorResult(Token::Value _operator) const
{
	if (_operator == Token::Delete)
		return make_shared<TupleType>();
	return (_operator == Token::Not) ? shared_from_this() : TypePointer();
}

TypePointer BoolType::binaryOperatorResult(Token::Value _operator, TypePointer const& _other) const
{
	if (category() != _other->category())
		return TypePointer();
	if (Token::isCompareOp(_operator) || _operator == Token::And || _operator == Token::Or)
		return _other;
	else
		return TypePointer();
}

bool ContractType::isImplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (*this == _convertTo)
		return true;
	if (_convertTo.category() == Category::Integer)
		return dynamic_cast<IntegerType const&>(_convertTo).isAddress();
	if (_convertTo.category() == Category::Contract)
	{
		auto const& bases = contractDefinition().annotation().linearizedBaseContracts;
		if (m_super && bases.size() <= 1)
			return false;
		return find(m_super ? ++bases.begin() : bases.begin(), bases.end(),
					&dynamic_cast<ContractType const&>(_convertTo).contractDefinition()) != bases.end();
	}
	return false;
}

bool ContractType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	return
		isImplicitlyConvertibleTo(_convertTo) ||
		_convertTo.category() == Category::Integer ||
		_convertTo.category() == Category::Contract;
}

bool ContractType::isPayable() const
{
	auto fallbackFunction = m_contract.fallbackFunction();
	return fallbackFunction && fallbackFunction->isPayable();
}

TypePointer ContractType::unaryOperatorResult(Token::Value _operator) const
{
	return _operator == Token::Delete ? make_shared<TupleType>() : TypePointer();
}

TypePointer ReferenceType::unaryOperatorResult(Token::Value _operator) const
{
	if (_operator != Token::Delete)
		return TypePointer();
	// delete can be used on everything except calldata references or storage pointers
	// (storage references are ok)
	switch (location())
	{
	case DataLocation::CallData:
		return TypePointer();
	case DataLocation::Memory:
		return make_shared<TupleType>();
	case DataLocation::Storage:
		return m_isPointer ? TypePointer() : make_shared<TupleType>();
	default:
		solAssert(false, "");
	}
	return TypePointer();
}

TypePointer ReferenceType::copyForLocationIfReference(DataLocation _location, TypePointer const& _type)
{
	if (auto type = dynamic_cast<ReferenceType const*>(_type.get()))
		return type->copyForLocation(_location, false);
	return _type;
}

TypePointer ReferenceType::copyForLocationIfReference(TypePointer const& _type) const
{
	return copyForLocationIfReference(m_location, _type);
}

string ReferenceType::stringForReferencePart() const
{
	switch (m_location)
	{
	case DataLocation::Storage:
		return string("storage ") + (m_isPointer ? "pointer" : "ref");
	case DataLocation::CallData:
		return "calldata";
	case DataLocation::Memory:
		return "memory";
	}
	solAssert(false, "");
	return "";
}

string ReferenceType::identifierLocationSuffix() const
{
	string id;
	if (location() == DataLocation::Storage)
		id += "_storage";
	else if (location() == DataLocation::Memory)
		id += "_memory";
	else
		id += "_calldata";
	if (isPointer())
		id += "_ptr";
	return id;
}

bool ArrayType::isImplicitlyConvertibleTo(const Type& _convertTo) const
{
	if (_convertTo.category() != category())
		return false;
	auto& convertTo = dynamic_cast<ArrayType const&>(_convertTo);
	if (convertTo.isByteArray() != isByteArray() || convertTo.isString() != isString())
		return false;
	// memory/calldata to storage can be converted, but only to a direct storage reference
	if (convertTo.location() == DataLocation::Storage && location() != DataLocation::Storage && convertTo.isPointer())
		return false;
	if (convertTo.location() == DataLocation::CallData && location() != convertTo.location())
		return false;
	if (convertTo.location() == DataLocation::Storage && !convertTo.isPointer())
	{
		// Less restrictive conversion, since we need to copy anyway.
		if (!baseType()->isImplicitlyConvertibleTo(*convertTo.baseType()))
			return false;
		if (convertTo.isDynamicallySized())
			return true;
		return !isDynamicallySized() && convertTo.length() >= length();
	}
	else
	{
		// Conversion to storage pointer or to memory, we de not copy element-for-element here, so
		// require that the base type is the same, not only convertible.
		// This disallows assignment of nested dynamic arrays from storage to memory for now.
		if (
			*copyForLocationIfReference(location(), baseType()) !=
			*copyForLocationIfReference(location(), convertTo.baseType())
		)
			return false;
		if (isDynamicallySized() != convertTo.isDynamicallySized())
			return false;
		// We also require that the size is the same.
		if (!isDynamicallySized() && length() != convertTo.length())
			return false;
		return true;
	}
}

bool ArrayType::isExplicitlyConvertibleTo(const Type& _convertTo) const
{
	if (isImplicitlyConvertibleTo(_convertTo))
		return true;
	// allow conversion bytes <-> string
	if (_convertTo.category() != category())
		return false;
	auto& convertTo = dynamic_cast<ArrayType const&>(_convertTo);
	if (convertTo.location() != location())
		return false;
	if (!isByteArray() || !convertTo.isByteArray())
		return false;
	return true;
}

string ArrayType::identifier() const
{
	string id;
	if (isString())
		id = "t_string";
	else if (isByteArray())
		id = "t_bytes";
	else
	{
		id = "t_array";
		id += identifierList(baseType());
		if (isDynamicallySized())
			id += "dyn";
		else
			id += length().str();
	}
	id += identifierLocationSuffix();

	return id;
}

bool ArrayType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	ArrayType const& other = dynamic_cast<ArrayType const&>(_other);
	if (
		!ReferenceType::operator==(other) ||
		other.isByteArray() != isByteArray() ||
		other.isString() != isString() ||
		other.isDynamicallySized() != isDynamicallySized()
	)
		return false;
	if (*other.baseType() != *baseType())
		return false;
	return isDynamicallySized() || length() == other.length();
}

bool ArrayType::validForCalldata() const
{
	return unlimitedCalldataEncodedSize(true) <= numeric_limits<unsigned>::max();
}

bigint ArrayType::unlimitedCalldataEncodedSize(bool _padded) const
{
	if (isDynamicallySized())
		return 16;
	bigint size = bigint(length()) * (isByteArray() ? 1 : baseType()->calldataEncodedSize(_padded));
	size = ((size + 15) / 16) * 16;
	return size;
}

unsigned ArrayType::calldataEncodedSize(bool _padded) const
{
	bigint size = unlimitedCalldataEncodedSize(_padded);
	solAssert(size <= numeric_limits<unsigned>::max(), "Array size does not fit unsigned.");
	return unsigned(size);
}

u256 ArrayType::storageSize() const
{
	if (isDynamicallySized())
		return 1;

	bigint size;
	unsigned baseBytes = baseType()->storageBytes();
	if (baseBytes == 0)
		size = 1;
	else if (baseBytes < 16)
	{
		unsigned itemsPerSlot = 16 / baseBytes;
		size = (bigint(length()) + (itemsPerSlot - 1)) / itemsPerSlot;
	}
	else
		size = bigint(length()) * baseType()->storageSize();
	if (size >= bigint(1) << 128)
		BOOST_THROW_EXCEPTION(Error(Error::Type::TypeError) << errinfo_comment("Array too large for storage."));
	return max<u256>(1, u256(size));
}

unsigned ArrayType::sizeOnStack() const
{
	if (m_location == DataLocation::CallData)
		// offset [length] (stack top)
		return 1 + (isDynamicallySized() ? 1 : 0);
	else
		// storage slot or memory offset
		// byte offset inside storage value is omitted
		return 1;
}

string ArrayType::toString(bool _short) const
{
	string ret;
	if (isString())
		ret = "string";
	else if (isByteArray())
		ret = "bytes";
	else
	{
		ret = baseType()->toString(_short) + "[";
		if (!isDynamicallySized())
			ret += length().str();
		ret += "]";
	}
	if (!_short)
		ret += " " + stringForReferencePart();
	return ret;
}

string ArrayType::canonicalName(bool _addDataLocation) const
{
	string ret;
	if (isString())
		ret = "string";
	else if (isByteArray())
		ret = "bytes";
	else
	{
		ret = baseType()->canonicalName(false) + "[";
		if (!isDynamicallySized())
			ret += length().str();
		ret += "]";
	}
	if (_addDataLocation && location() == DataLocation::Storage)
		ret += " storage";
	return ret;
}

MemberList::MemberMap ArrayType::nativeMembers(ContractDefinition const*) const
{
	MemberList::MemberMap members;
	if (!isString())
	{
		members.push_back({"length", make_shared<IntegerType>(128)});
		if (isDynamicallySized() && location() == DataLocation::Storage)
			members.push_back({"push", make_shared<FunctionType>(
				TypePointers{baseType()},
				TypePointers{make_shared<IntegerType>(128)},
				strings{string()},
				strings{string()},
				isByteArray() ? FunctionType::Kind::ByteArrayPush : FunctionType::Kind::ArrayPush
			)});
	}
	return members;
}

TypePointer ArrayType::encodingType() const
{
	if (location() == DataLocation::Storage)
		return make_shared<IntegerType>(128);
	else
		return this->copyForLocation(DataLocation::Memory, true);
}

TypePointer ArrayType::decodingType() const
{
	if (location() == DataLocation::Storage)
		return make_shared<IntegerType>(128);
	else
		return shared_from_this();
}

TypePointer ArrayType::interfaceType(bool _inLibrary) const
{
	// Note: This has to fulfill canBeUsedExternally(_inLibrary) ==  !!interfaceType(_inLibrary)
	if (_inLibrary && location() == DataLocation::Storage)
		return shared_from_this();

	if (m_arrayKind != ArrayKind::Ordinary)
		return this->copyForLocation(DataLocation::Memory, true);
	TypePointer baseExt = m_baseType->interfaceType(_inLibrary);
	if (!baseExt)
		return TypePointer();
	if (m_baseType->category() == Category::Array && m_baseType->isDynamicallySized())
		return TypePointer();

	if (isDynamicallySized())
		return make_shared<ArrayType>(DataLocation::Memory, baseExt);
	else
		return make_shared<ArrayType>(DataLocation::Memory, baseExt, m_length);
}

bool ArrayType::canBeUsedExternally(bool _inLibrary) const
{
	// Note: This has to fulfill canBeUsedExternally(_inLibrary) ==  !!interfaceType(_inLibrary)
	if (_inLibrary && location() == DataLocation::Storage)
		return true;
	else if (m_arrayKind != ArrayKind::Ordinary)
		return true;
	else if (!m_baseType->canBeUsedExternally(_inLibrary))
		return false;
	else if (m_baseType->category() == Category::Array && m_baseType->isDynamicallySized())
		return false;
	else
		return true;
}

u256 ArrayType::memorySize() const
{
	solAssert(!isDynamicallySized(), "");
	solAssert(m_location == DataLocation::Memory, "");
	bigint size = bigint(m_length) * m_baseType->memoryHeadSize();
	solAssert(size <= numeric_limits<unsigned>::max(), "Array size does not fit u256.");
	return u256(size);
}

TypePointer ArrayType::copyForLocation(DataLocation _location, bool _isPointer) const
{
	auto copy = make_shared<ArrayType>(_location);
	copy->m_isPointer = _isPointer;
	copy->m_arrayKind = m_arrayKind;
	copy->m_baseType = copy->copyForLocationIfReference(m_baseType);
	copy->m_hasDynamicLength = m_hasDynamicLength;
	copy->m_length = m_length;
	return copy;
}

string ContractType::identifier() const
{
	return (m_super ? "t_super" : "t_contract") + parenthesizeUserIdentifier(m_contract.name()) + std::to_string(m_contract.id());
}

bool ContractType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	ContractType const& other = dynamic_cast<ContractType const&>(_other);
	return other.m_contract == m_contract && other.m_super == m_super;
}

string ContractType::toString(bool) const
{
	return
		string(m_contract.isLibrary() ? "library " : "contract ") +
		string(m_super ? "super " : "") +
		m_contract.name();
}

string ContractType::canonicalName(bool) const
{
	return m_contract.annotation().canonicalName;
}

MemberList::MemberMap ContractType::nativeMembers(ContractDefinition const*) const
{
	// All address members and all interface functions
	MemberList::MemberMap members(IntegerType(120, IntegerType::Modifier::Address).nativeMembers(nullptr));
	if (m_super)
	{
		// add the most derived of all functions which are visible in derived contracts
		auto bases = m_contract.annotation().linearizedBaseContracts;
		solAssert(bases.size() >= 1, "linearizedBaseContracts should at least contain the most derived contract.");
		// `sliced(1, ...)` ignores the most derived contract, which should not be searchable from `super`.
		for (ContractDefinition const* base: bases | boost::adaptors::sliced(1, bases.size()))
			for (FunctionDefinition const* function: base->definedFunctions())
			{
				if (!function->isVisibleInDerivedContracts())
					continue;
				auto functionType = make_shared<FunctionType>(*function, true);
				bool functionWithEqualArgumentsFound = false;
				for (auto const& member: members)
				{
					if (member.name != function->name())
						continue;
					auto memberType = dynamic_cast<FunctionType const*>(member.type.get());
					solAssert(!!memberType, "Override changes type.");
					if (!memberType->hasEqualArgumentTypes(*functionType))
						continue;
					functionWithEqualArgumentsFound = true;
					break;
				}
				if (!functionWithEqualArgumentsFound)
					members.push_back(MemberList::Member(
						function->name(),
						functionType,
						function
					));
			}
	}
	else if (!m_contract.isLibrary())
	{
		for (auto const& it: m_contract.interfaceFunctions())
			members.push_back(MemberList::Member(
				it.second->declaration().name(),
				it.second->asMemberFunction(m_contract.isLibrary()),
				&it.second->declaration()
			));
	}
	return members;
}

shared_ptr<FunctionType const> const& ContractType::newExpressionType() const
{
	if (!m_constructorType)
		m_constructorType = FunctionType::newExpressionType(m_contract);
	return m_constructorType;
}

vector<tuple<VariableDeclaration const*, u256, unsigned>> ContractType::stateVariables() const
{
	vector<VariableDeclaration const*> variables;
	for (ContractDefinition const* contract: boost::adaptors::reverse(m_contract.annotation().linearizedBaseContracts))
		for (VariableDeclaration const* variable: contract->stateVariables())
			if (!variable->isConstant())
				variables.push_back(variable);
	TypePointers types;
	for (auto variable: variables)
		types.push_back(variable->annotation().type);
	StorageOffsets offsets;
	offsets.computeOffsets(types);

	vector<tuple<VariableDeclaration const*, u256, unsigned>> variablesAndOffsets;
	for (size_t index = 0; index < variables.size(); ++index)
		if (auto const* offset = offsets.offset(index))
			variablesAndOffsets.push_back(make_tuple(variables[index], offset->first, offset->second));
	return variablesAndOffsets;
}

bool StructType::isImplicitlyConvertibleTo(const Type& _convertTo) const
{
	if (_convertTo.category() != category())
		return false;
	auto& convertTo = dynamic_cast<StructType const&>(_convertTo);
	// memory/calldata to storage can be converted, but only to a direct storage reference
	if (convertTo.location() == DataLocation::Storage && location() != DataLocation::Storage && convertTo.isPointer())
		return false;
	if (convertTo.location() == DataLocation::CallData && location() != convertTo.location())
		return false;
	return this->m_struct == convertTo.m_struct;
}

string StructType::identifier() const
{
	return "t_struct" + parenthesizeUserIdentifier(m_struct.name()) + std::to_string(m_struct.id()) + identifierLocationSuffix();
}

bool StructType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	StructType const& other = dynamic_cast<StructType const&>(_other);
	return ReferenceType::operator==(other) && other.m_struct == m_struct;
}

unsigned StructType::calldataEncodedSize(bool _padded) const
{
	unsigned size = 0;
	for (auto const& member: members(nullptr))
		if (!member.type->canLiveOutsideStorage())
			return 0;
		else
		{
			unsigned memberSize = member.type->calldataEncodedSize(_padded);
			if (memberSize == 0)
				return 0;
			size += memberSize;
		}
	return size;
}

u256 StructType::memorySize() const
{
	u256 size;
	for (auto const& member: members(nullptr))
		if (member.type->canLiveOutsideStorage())
			size += member.type->memoryHeadSize();
	return size;
}

u256 StructType::storageSize() const
{
	return max<u256>(1, members(nullptr).storageSize());
}

string StructType::toString(bool _short) const
{
	string ret = "struct " + m_struct.annotation().canonicalName;
	if (!_short)
		ret += " " + stringForReferencePart();
	return ret;
}

MemberList::MemberMap StructType::nativeMembers(ContractDefinition const*) const
{
	MemberList::MemberMap members;
	for (ASTPointer<VariableDeclaration> const& variable: m_struct.members())
	{
		TypePointer type = variable->annotation().type;
		solAssert(type, "");
		// Skip all mapping members if we are not in storage.
		if (location() != DataLocation::Storage && !type->canLiveOutsideStorage())
			continue;
		members.push_back(MemberList::Member(
			variable->name(),
			copyForLocationIfReference(type),
			variable.get())
		);
	}
	return members;
}

TypePointer StructType::interfaceType(bool _inLibrary) const
{
	if (_inLibrary && location() == DataLocation::Storage)
		return shared_from_this();
	else
		return TypePointer();
}

TypePointer StructType::copyForLocation(DataLocation _location, bool _isPointer) const
{
	auto copy = make_shared<StructType>(m_struct, _location);
	copy->m_isPointer = _isPointer;
	return copy;
}

string StructType::canonicalName(bool _addDataLocation) const
{
	string ret = m_struct.annotation().canonicalName;
	if (_addDataLocation && location() == DataLocation::Storage)
		ret += " storage";
	return ret;
}

FunctionTypePointer StructType::constructorType() const
{
	TypePointers paramTypes;
	strings paramNames;
	for (auto const& member: members(nullptr))
	{
		if (!member.type->canLiveOutsideStorage())
			continue;
		paramNames.push_back(member.name);
		paramTypes.push_back(copyForLocationIfReference(DataLocation::Memory, member.type));
	}
	return make_shared<FunctionType>(
		paramTypes,
		TypePointers{copyForLocation(DataLocation::Memory, false)},
		paramNames,
		strings(),
		FunctionType::Kind::Internal
	);
}

pair<u128, unsigned> const& StructType::storageOffsetsOfMember(string const& _name) const
{
	auto const* offsets = members(nullptr).memberStorageOffset(_name);
	solAssert(offsets, "Storage offset of non-existing member requested.");
	return *offsets;
}

u128 StructType::memoryOffsetOfMember(string const& _name) const
{
	u128 offset;
	for (auto const& member: members(nullptr))
		if (member.name == _name)
			return offset;
		else
			offset += member.type->memoryHeadSize();
	solAssert(false, "Member not found in struct.");
	return 0;
}

set<string> StructType::membersMissingInMemory() const
{
	set<string> missing;
	for (ASTPointer<VariableDeclaration> const& variable: m_struct.members())
		if (!variable->annotation().type->canLiveOutsideStorage())
			missing.insert(variable->name());
	return missing;
}

TypePointer EnumType::unaryOperatorResult(Token::Value _operator) const
{
	return _operator == Token::Delete ? make_shared<TupleType>() : TypePointer();
}

string EnumType::identifier() const
{
	return "t_enum" + parenthesizeUserIdentifier(m_enum.name()) + std::to_string(m_enum.id());
}

bool EnumType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	EnumType const& other = dynamic_cast<EnumType const&>(_other);
	return other.m_enum == m_enum;
}

unsigned EnumType::storageBytes() const
{
	size_t elements = numberOfMembers();
	if (elements <= 1)
		return 1;
	else
		return dev::bytesRequired(elements - 1);
}

string EnumType::toString(bool) const
{
	return string("enum ") + m_enum.annotation().canonicalName;
}

string EnumType::canonicalName(bool) const
{
	return m_enum.annotation().canonicalName;
}

size_t EnumType::numberOfMembers() const
{
	return m_enum.members().size();
};

bool EnumType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	return _convertTo == *this || _convertTo.category() == Category::Integer;
}

unsigned EnumType::memberValue(ASTString const& _member) const
{
	unsigned index = 0;
	for (ASTPointer<EnumValue> const& decl: m_enum.members())
	{
		if (decl->name() == _member)
			return index;
		++index;
	}
	BOOST_THROW_EXCEPTION(m_enum.createTypeError("Requested unknown enum value ." + _member));
}

bool TupleType::isImplicitlyConvertibleTo(Type const& _other) const
{
	if (auto tupleType = dynamic_cast<TupleType const*>(&_other))
	{
		TypePointers const& targets = tupleType->components();
		if (targets.empty())
			return components().empty();
		if (components().size() != targets.size() && !targets.front() && !targets.back())
			return false; // (,a,) = (1,2,3,4) - unable to position `a` in the tuple.
		size_t minNumValues = targets.size();
		if (!targets.back() || !targets.front())
			--minNumValues; // wildcards can also match 0 components
		if (components().size() < minNumValues)
			return false;
		if (components().size() > targets.size() && targets.front() && targets.back())
			return false; // larger source and no wildcard
		bool fillRight = !targets.back() || targets.front();
		for (size_t i = 0; i < min(targets.size(), components().size()); ++i)
		{
			auto const& s = components()[fillRight ? i : components().size() - i - 1];
			auto const& t = targets[fillRight ? i : targets.size() - i - 1];
			if (!s && t)
				return false;
			else if (s && t && !s->isImplicitlyConvertibleTo(*t))
				return false;
		}
		return true;
	}
	else
		return false;
}

string TupleType::identifier() const
{
	return "t_tuple" + identifierList(components());
}

bool TupleType::operator==(Type const& _other) const
{
	if (auto tupleType = dynamic_cast<TupleType const*>(&_other))
		return components() == tupleType->components();
	else
		return false;
}

string TupleType::toString(bool _short) const
{
	if (components().empty())
		return "tuple()";
	string str = "tuple(";
	for (auto const& t: components())
		str += (t ? t->toString(_short) : "") + ",";
	str.pop_back();
	return str + ")";
}

u256 TupleType::storageSize() const
{
	solAssert(false, "Storage size of non-storable tuple type requested.");
}

unsigned TupleType::sizeOnStack() const
{
	unsigned size = 0;
	for (auto const& t: components())
		size += t ? t->sizeOnStack() : 0;
	return size;
}

TypePointer TupleType::mobileType() const
{
	TypePointers mobiles;
	for (auto const& c: components())
	{
		if (c)
		{
			auto mt = c->mobileType();
			if (!mt)
				return TypePointer();
			mobiles.push_back(mt);
		}
		else
			mobiles.push_back(TypePointer());
	}
	return make_shared<TupleType>(mobiles);
}

TypePointer TupleType::closestTemporaryType(TypePointer const& _targetType) const
{
	solAssert(!!_targetType, "");
	TypePointers const& targetComponents = dynamic_cast<TupleType const&>(*_targetType).components();
	bool fillRight = !targetComponents.empty() && (!targetComponents.back() || targetComponents.front());
	TypePointers tempComponents(targetComponents.size());
	for (size_t i = 0; i < min(targetComponents.size(), components().size()); ++i)
	{
		size_t si = fillRight ? i : components().size() - i - 1;
		size_t ti = fillRight ? i : targetComponents.size() - i - 1;
		if (components()[si] && targetComponents[ti])
		{
			tempComponents[ti] = components()[si]->closestTemporaryType(targetComponents[ti]);
			solAssert(tempComponents[ti], "");
		}
	}
	return make_shared<TupleType>(tempComponents);
}

FunctionType::FunctionType(FunctionDefinition const& _function, bool _isInternal):
	m_kind(_isInternal ? Kind::Internal : Kind::External),
	m_isConstant(_function.isDeclaredConst()),
	m_isPayable(_isInternal ? false : _function.isPayable()),
	m_declaration(&_function)
{
	TypePointers params;
	vector<string> paramNames;
	TypePointers retParams;
	vector<string> retParamNames;

	params.reserve(_function.parameters().size());
	paramNames.reserve(_function.parameters().size());
	for (ASTPointer<VariableDeclaration> const& var: _function.parameters())
	{
		paramNames.push_back(var->name());
		params.push_back(var->annotation().type);
	}
	retParams.reserve(_function.returnParameters().size());
	retParamNames.reserve(_function.returnParameters().size());
	for (ASTPointer<VariableDeclaration> const& var: _function.returnParameters())
	{
		retParamNames.push_back(var->name());
		retParams.push_back(var->annotation().type);
	}
	swap(params, m_parameterTypes);
	swap(paramNames, m_parameterNames);
	swap(retParams, m_returnParameterTypes);
	swap(retParamNames, m_returnParameterNames);
}

FunctionType::FunctionType(VariableDeclaration const& _varDecl):
	m_kind(Kind::External), m_isConstant(true), m_declaration(&_varDecl)
{
	TypePointers paramTypes;
	vector<string> paramNames;
	auto returnType = _varDecl.annotation().type;

	while (true)
	{
		if (auto mappingType = dynamic_cast<MappingType const*>(returnType.get()))
		{
			paramTypes.push_back(mappingType->keyType());
			paramNames.push_back("");
			returnType = mappingType->valueType();
		}
		else if (auto arrayType = dynamic_cast<ArrayType const*>(returnType.get()))
		{
			if (arrayType->isByteArray())
				// Return byte arrays as as whole.
				break;
			returnType = arrayType->baseType();
			paramNames.push_back("");
			paramTypes.push_back(make_shared<IntegerType>(128));
		}
		else
			break;
	}

	TypePointers retParams;
	vector<string> retParamNames;
	if (auto structType = dynamic_cast<StructType const*>(returnType.get()))
	{
		for (auto const& member: structType->members(nullptr))
		{
			solAssert(member.type, "");
			if (member.type->category() != Category::Mapping)
			{
				if (auto arrayType = dynamic_cast<ArrayType const*>(member.type.get()))
					if (!arrayType->isByteArray())
						continue;
				retParams.push_back(member.type);
				retParamNames.push_back(member.name);
			}
		}
	}
	else
	{
		retParams.push_back(ReferenceType::copyForLocationIfReference(
			DataLocation::Memory,
			returnType
		));
		retParamNames.push_back("");
	}

	swap(paramTypes, m_parameterTypes);
	swap(paramNames, m_parameterNames);
	swap(retParams, m_returnParameterTypes);
	swap(retParamNames, m_returnParameterNames);
}

FunctionType::FunctionType(EventDefinition const& _event):
	m_kind(Kind::Event), m_isConstant(true), m_declaration(&_event)
{
	TypePointers params;
	vector<string> paramNames;
	params.reserve(_event.parameters().size());
	paramNames.reserve(_event.parameters().size());
	for (ASTPointer<VariableDeclaration> const& var: _event.parameters())
	{
		paramNames.push_back(var->name());
		params.push_back(var->annotation().type);
	}
	swap(params, m_parameterTypes);
	swap(paramNames, m_parameterNames);
}

FunctionType::FunctionType(FunctionTypeName const& _typeName):
	m_kind(_typeName.visibility() == VariableDeclaration::Visibility::External ? Kind::External : Kind::Internal),
	m_isConstant(_typeName.isDeclaredConst()),
	m_isPayable(_typeName.isPayable())
{
	if (_typeName.isPayable())
	{
		solAssert(m_kind == Kind::External, "Internal payable function type used.");
		solAssert(!m_isConstant, "Payable constant function");
	}
	for (auto const& t: _typeName.parameterTypes())
	{
		solAssert(t->annotation().type, "Type not set for parameter.");
		if (m_kind == Kind::External)
			solAssert(
				t->annotation().type->canBeUsedExternally(false),
				"Internal type used as parameter for external function."
			);
		m_parameterTypes.push_back(t->annotation().type);
	}
	for (auto const& t: _typeName.returnParameterTypes())
	{
		solAssert(t->annotation().type, "Type not set for return parameter.");
		if (m_kind == Kind::External)
			solAssert(
				t->annotation().type->canBeUsedExternally(false),
				"Internal type used as return parameter for external function."
			);
		m_returnParameterTypes.push_back(t->annotation().type);
	}
}

FunctionTypePointer FunctionType::newExpressionType(ContractDefinition const& _contract)
{
	FunctionDefinition const* constructor = _contract.constructor();
	TypePointers parameters;
	strings parameterNames;
	bool payable = false;

	if (constructor)
	{
		for (ASTPointer<VariableDeclaration> const& var: constructor->parameters())
		{
			parameterNames.push_back(var->name());
			parameters.push_back(var->annotation().type);
		}
		payable = constructor->isPayable();
	}
	return make_shared<FunctionType>(
		parameters,
		TypePointers{make_shared<ContractType>(_contract)},
		parameterNames,
		strings{""},
		Kind::Creation,
		false,
		nullptr,
		false,
		payable
	);
}

vector<string> FunctionType::parameterNames() const
{
	if (!bound())
		return m_parameterNames;
	return vector<string>(m_parameterNames.cbegin() + 1, m_parameterNames.cend());
}

TypePointers FunctionType::parameterTypes() const
{
	if (!bound())
		return m_parameterTypes;
	return TypePointers(m_parameterTypes.cbegin() + 1, m_parameterTypes.cend());
}

string FunctionType::identifier() const
{
	string id = "t_function_";
	switch (m_kind)
	{
	case Kind::Internal: id += "internal"; break;
	case Kind::External: id += "external"; break;
	case Kind::CallCode: id += "callcode"; break;
	case Kind::DelegateCall: id += "delegatecall"; break;
	case Kind::BareCall: id += "barecall"; break;
	case Kind::BareCallCode: id += "barecallcode"; break;
	case Kind::BareDelegateCall: id += "baredelegatecall"; break;
	case Kind::Creation: id += "creation"; break;
	case Kind::Send: id += "send"; break;
	case Kind::Transfer: id += "transfer"; break;
	case Kind::SHA3: id += "sha3"; break;
	case Kind::Selfdestruct: id += "selfdestruct"; break;
	case Kind::Revert: id += "revert"; break;
	case Kind::ECRecover: id += "ecrecover"; break;
	case Kind::EDVerify: id += "edverify"; break;
	case Kind::SHA256: id += "sha256"; break;
	case Kind::RIPEMD160: id += "ripemd160"; break;
	case Kind::Log0: id += "log0"; break;
	case Kind::Log1: id += "log1"; break;
	case Kind::Log2: id += "log2"; break;
	case Kind::Log3: id += "log3"; break;
	case Kind::Log4: id += "log4"; break;
	case Kind::Event: id += "event"; break;
	case Kind::SetGas: id += "setgas"; break;
	case Kind::SetValue: id += "setvalue"; break;
	case Kind::BlockHash: id += "blockhash"; break;
	case Kind::AddMod: id += "addmod"; break;
	case Kind::MulMod: id += "mulmod"; break;
	case Kind::ArrayPush: id += "arraypush"; break;
	case Kind::ByteArrayPush: id += "bytearraypush"; break;
	case Kind::ObjectCreation: id += "objectcreation"; break;
	case Kind::Assert: id += "assert"; break;
	case Kind::Require: id += "require";break;
	default: solAssert(false, "Unknown function location."); break;
	}
	if (isConstant())
		id += "_constant";
	id += identifierList(m_parameterTypes) + "returns" + identifierList(m_returnParameterTypes);
	if (m_gasSet)
		id += "gas";
	if (m_valueSet)
		id += "value";
	if (bound())
		id += "bound_to" + identifierList(selfType());
	return id;
}

bool FunctionType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	FunctionType const& other = dynamic_cast<FunctionType const&>(_other);

	if (m_kind != other.m_kind)
		return false;
	if (m_isConstant != other.isConstant())
		return false;

	if (m_parameterTypes.size() != other.m_parameterTypes.size() ||
			m_returnParameterTypes.size() != other.m_returnParameterTypes.size())
		return false;
	auto typeCompare = [](TypePointer const& _a, TypePointer const& _b) -> bool { return *_a == *_b; };

	if (!equal(m_parameterTypes.cbegin(), m_parameterTypes.cend(),
			   other.m_parameterTypes.cbegin(), typeCompare))
		return false;
	if (!equal(m_returnParameterTypes.cbegin(), m_returnParameterTypes.cend(),
			   other.m_returnParameterTypes.cbegin(), typeCompare))
		return false;
	//@todo this is ugly, but cannot be prevented right now
	if (m_gasSet != other.m_gasSet || m_valueSet != other.m_valueSet)
		return false;
	if (bound() != other.bound())
		return false;
	if (bound() && *selfType() != *other.selfType())
		return false;
	return true;
}

bool FunctionType::isExplicitlyConvertibleTo(Type const& _convertTo) const
{
	if (m_kind == Kind::External && _convertTo.category() == Category::Integer)
	{
		IntegerType const& convertTo = dynamic_cast<IntegerType const&>(_convertTo);
		if (convertTo.isAddress())
			return true;
	}
	return _convertTo.category() == category();
}

TypePointer FunctionType::unaryOperatorResult(Token::Value _operator) const
{
	if (_operator == Token::Value::Delete)
		return make_shared<TupleType>();
	return TypePointer();
}

TypePointer FunctionType::binaryOperatorResult(Token::Value _operator, TypePointer const& _other) const
{
	if (_other->category() != category() || !(_operator == Token::Equal || _operator == Token::NotEqual))
		return TypePointer();
	FunctionType const& other = dynamic_cast<FunctionType const&>(*_other);
	if (kind() == Kind::Internal && other.kind() == Kind::Internal && sizeOnStack() == 1 && other.sizeOnStack() == 1)
		return commonType(shared_from_this(), _other);
	return TypePointer();
}

string FunctionType::canonicalName(bool) const
{
	solAssert(m_kind == Kind::External, "");
	return "function";
}

string FunctionType::toString(bool _short) const
{
	string name = "function (";
	for (auto it = m_parameterTypes.begin(); it != m_parameterTypes.end(); ++it)
		name += (*it)->toString(_short) + (it + 1 == m_parameterTypes.end() ? "" : ",");
	name += ")";
	if (m_isConstant)
		name += " constant";
	if (m_isPayable)
		name += " payable";
	if (m_kind == Kind::External)
		name += " external";
	if (!m_returnParameterTypes.empty())
	{
		name += " returns (";
		for (auto it = m_returnParameterTypes.begin(); it != m_returnParameterTypes.end(); ++it)
			name += (*it)->toString(_short) + (it + 1 == m_returnParameterTypes.end() ? "" : ",");
		name += ")";
	}
	return name;
}

unsigned FunctionType::calldataEncodedSize(bool _padded) const
{
	unsigned size = storageBytes();
	if (_padded)
		size = ((size + 15) / 16) * 16;
	return size;
}

u256 FunctionType::storageSize() const
{
	if (m_kind == Kind::External)
		return 3;
	else if (m_kind == Kind::Internal)
		return 1;
	else
		solAssert(false, "Storage size of non-storable function type requested.");
}

unsigned FunctionType::storageBytes() const
{
	if (m_kind == Kind::External)
		return 32 + 4;
	else if (m_kind == Kind::Internal)
		return 8; // it should really not be possible to create larger programs
	else
		solAssert(false, "Storage size of non-storable function type requested.");
}

unsigned FunctionType::sizeOnStack() const
{
	Kind kind = m_kind;
	if (m_kind == Kind::SetGas || m_kind == Kind::SetValue)
	{
		solAssert(m_returnParameterTypes.size() == 1, "");
		kind = dynamic_cast<FunctionType const&>(*m_returnParameterTypes.front()).m_kind;
	}

	unsigned size = 0;
	if (kind == Kind::External || kind == Kind::CallCode || kind == Kind::DelegateCall)
		size = 3;
	else if (kind == Kind::BareCall || kind == Kind::BareCallCode || kind == Kind::BareDelegateCall)
		size = 2;
	else if (kind == Kind::Internal)
		size = 1;
	else if (kind == Kind::ArrayPush || kind == Kind::ByteArrayPush)
		size = 1;
	if (m_gasSet)
		size++;
	if (m_valueSet)
		size++;
	if (bound())
		size += m_parameterTypes.front()->sizeOnStack();
	return size;
}

FunctionTypePointer FunctionType::interfaceFunctionType() const
{
	// Note that m_declaration might also be a state variable!
	solAssert(m_declaration, "Declaration needed to determine interface function type.");
	bool isLibraryFunction = dynamic_cast<ContractDefinition const&>(*m_declaration->scope()).isLibrary();

	TypePointers paramTypes;
	TypePointers retParamTypes;

	for (auto type: m_parameterTypes)
	{
		if (auto ext = type->interfaceType(isLibraryFunction))
			paramTypes.push_back(ext);
		else
			return FunctionTypePointer();
	}
	for (auto type: m_returnParameterTypes)
	{
		if (auto ext = type->interfaceType(isLibraryFunction))
			retParamTypes.push_back(ext);
		else
			return FunctionTypePointer();
	}
	auto variable = dynamic_cast<VariableDeclaration const*>(m_declaration);
	if (variable && retParamTypes.empty())
		return FunctionTypePointer();

	return make_shared<FunctionType>(
		paramTypes, retParamTypes,
		m_parameterNames, m_returnParameterNames,
		m_kind, m_arbitraryParameters,
		m_declaration, m_isConstant, m_isPayable
	);
}

MemberList::MemberMap FunctionType::nativeMembers(ContractDefinition const*) const
{
	switch (m_kind)
	{
	case Kind::External:
	case Kind::Creation:
	case Kind::BareCall:
	case Kind::BareCallCode:
	case Kind::BareDelegateCall:
	{
		MemberList::MemberMap members;
		if (m_kind != Kind::BareDelegateCall && m_kind != Kind::DelegateCall)
		{
			if (m_isPayable)
				members.push_back(MemberList::Member(
					"value",
					make_shared<FunctionType>(
						parseElementaryTypeVector({"uint"}),
						TypePointers{copyAndSetGasOrValue(false, true)},
						strings(),
						strings(),
						Kind::SetValue,
						false,
						nullptr,
						false,
						false,
						m_gasSet,
						m_valueSet
					)
				));
		}
		if (m_kind != Kind::Creation)
			members.push_back(MemberList::Member(
				"gas",
				make_shared<FunctionType>(
					parseElementaryTypeVector({"uint"}),
					TypePointers{copyAndSetGasOrValue(true, false)},
					strings(),
					strings(),
					Kind::SetGas,
					false,
					nullptr,
					false,
					false,
					m_gasSet,
					m_valueSet
				)
			));
		return members;
	}
	default:
		return MemberList::MemberMap();
	}
}

TypePointer FunctionType::encodingType() const
{
	// Only external functions can be encoded, internal functions cannot leave code boundaries.
	if (m_kind == Kind::External)
		return shared_from_this();
	else
		return TypePointer();
}

TypePointer FunctionType::interfaceType(bool /*_inLibrary*/) const
{
	if (m_kind == Kind::External)
		return shared_from_this();
	else
		return TypePointer();
}

bool FunctionType::canTakeArguments(TypePointers const& _argumentTypes, TypePointer const& _selfType) const
{
	solAssert(!bound() || _selfType, "");
	if (bound() && !_selfType->isImplicitlyConvertibleTo(*selfType()))
		return false;
	TypePointers paramTypes = parameterTypes();
	if (takesArbitraryParameters())
		return true;
	else if (_argumentTypes.size() != paramTypes.size())
		return false;
	else
		return equal(
			_argumentTypes.cbegin(),
			_argumentTypes.cend(),
			paramTypes.cbegin(),
			[](TypePointer const& argumentType, TypePointer const& parameterType)
			{
				return argumentType->isImplicitlyConvertibleTo(*parameterType);
			}
		);
}

bool FunctionType::hasEqualArgumentTypes(FunctionType const& _other) const
{
	if (m_parameterTypes.size() != _other.m_parameterTypes.size())
		return false;
	return equal(
		m_parameterTypes.cbegin(),
		m_parameterTypes.cend(),
		_other.m_parameterTypes.cbegin(),
		[](TypePointer const& _a, TypePointer const& _b) -> bool { return *_a == *_b; }
	);
}

bool FunctionType::isBareCall() const
{
	switch (m_kind)
	{
	case Kind::BareCall:
	case Kind::BareCallCode:
	case Kind::BareDelegateCall:
	case Kind::ECRecover:
	case Kind::EDVerify:
	case Kind::SHA256:
	case Kind::RIPEMD160:
		return true;
	default:
		return false;
	}
}

string FunctionType::externalSignature() const
{
	solAssert(m_declaration != nullptr, "External signature of function needs declaration");
	solAssert(!m_declaration->name().empty(), "Fallback function has no signature.");

	bool _inLibrary = dynamic_cast<ContractDefinition const&>(*m_declaration->scope()).isLibrary();

	string ret = m_declaration->name() + "(";

	FunctionTypePointer external = interfaceFunctionType();
	solAssert(!!external, "External function type requested.");
	TypePointers externalParameterTypes = external->parameterTypes();
	for (auto it = externalParameterTypes.cbegin(); it != externalParameterTypes.cend(); ++it)
	{
		solAssert(!!(*it), "Parameter should have external type");
		ret += (*it)->canonicalName(_inLibrary) + (it + 1 == externalParameterTypes.cend() ? "" : ",");
	}

	return ret + ")";
}

u256 FunctionType::externalIdentifier() const
{
	return FixedHash<4>::Arith(FixedHash<4>(dev::keccak256(externalSignature())));
}

bool FunctionType::isPure() const
{
	return
		m_kind == Kind::SHA3 ||
		m_kind == Kind::ECRecover ||
		m_kind == Kind::EDVerify ||
		m_kind == Kind::SHA256 ||
		m_kind == Kind::RIPEMD160 ||
		m_kind == Kind::AddMod ||
		m_kind == Kind::MulMod ||
		m_kind == Kind::ObjectCreation;
}

TypePointers FunctionType::parseElementaryTypeVector(strings const& _types)
{
	TypePointers pointers;
	pointers.reserve(_types.size());
	for (string const& type: _types)
		pointers.push_back(Type::fromElementaryTypeName(type));
	return pointers;
}

TypePointer FunctionType::copyAndSetGasOrValue(bool _setGas, bool _setValue) const
{
	return make_shared<FunctionType>(
		m_parameterTypes,
		m_returnParameterTypes,
		m_parameterNames,
		m_returnParameterNames,
		m_kind,
		m_arbitraryParameters,
		m_declaration,
		m_isConstant,
		m_isPayable,
		m_gasSet || _setGas,
		m_valueSet || _setValue,
		m_bound
	);
}

FunctionTypePointer FunctionType::asMemberFunction(bool _inLibrary, bool _bound) const
{
	if (_bound && m_parameterTypes.empty())
		return FunctionTypePointer();

	TypePointers parameterTypes;
	for (auto const& t: m_parameterTypes)
	{
		auto refType = dynamic_cast<ReferenceType const*>(t.get());
		if (refType && refType->location() == DataLocation::CallData)
			parameterTypes.push_back(refType->copyForLocation(DataLocation::Memory, true));
		else
			parameterTypes.push_back(t);
	}

	Kind kind = m_kind;
	if (_inLibrary)
	{
		solAssert(!!m_declaration, "Declaration has to be available.");
		if (!m_declaration->isPublic())
			kind = Kind::Internal; // will be inlined
		else
			kind = Kind::DelegateCall;
	}

	TypePointers returnParameterTypes = m_returnParameterTypes;
	if (kind != Kind::Internal)
	{
		// Alter dynamic types to be non-accessible.
		for (auto& param: returnParameterTypes)
			if (param->isDynamicallySized())
				param = make_shared<InaccessibleDynamicType>();
	}

	return make_shared<FunctionType>(
		parameterTypes,
		returnParameterTypes,
		m_parameterNames,
		m_returnParameterNames,
		kind,
		m_arbitraryParameters,
		m_declaration,
		m_isConstant,
		m_isPayable,
		m_gasSet,
		m_valueSet,
		_bound
	);
}

TypePointer const& FunctionType::selfType() const
{
	solAssert(bound(), "Function is not bound.");
	solAssert(m_parameterTypes.size() > 0, "Function has no self type.");
	return m_parameterTypes.at(0);
}

ASTPointer<ASTString> FunctionType::documentation() const
{
	auto function = dynamic_cast<Documented const*>(m_declaration);
	if (function)
		return function->documentation();

	return ASTPointer<ASTString>();
}

string MappingType::identifier() const
{
	return "t_mapping" + identifierList(m_keyType, m_valueType);
}

bool MappingType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	MappingType const& other = dynamic_cast<MappingType const&>(_other);
	return *other.m_keyType == *m_keyType && *other.m_valueType == *m_valueType;
}

string MappingType::toString(bool _short) const
{
	return "mapping(" + keyType()->toString(_short) + " => " + valueType()->toString(_short) + ")";
}

string MappingType::canonicalName(bool) const
{
	return "mapping(" + keyType()->canonicalName(false) + " => " + valueType()->canonicalName(false) + ")";
}

string TypeType::identifier() const
{
	return "t_type" + identifierList(actualType());
}

bool TypeType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	TypeType const& other = dynamic_cast<TypeType const&>(_other);
	return *actualType() == *other.actualType();
}

u256 TypeType::storageSize() const
{
	solAssert(false, "Storage size of non-storable type type requested.");
}

unsigned TypeType::sizeOnStack() const
{
	if (auto contractType = dynamic_cast<ContractType const*>(m_actualType.get()))
		if (contractType->contractDefinition().isLibrary())
			return 1;
	return 0;
}

MemberList::MemberMap TypeType::nativeMembers(ContractDefinition const* _currentScope) const
{
	MemberList::MemberMap members;
	if (m_actualType->category() == Category::Contract)
	{
		ContractDefinition const& contract = dynamic_cast<ContractType const&>(*m_actualType).contractDefinition();
		bool isBase = false;
		if (_currentScope != nullptr)
		{
			auto const& currentBases = _currentScope->annotation().linearizedBaseContracts;
			isBase = (find(currentBases.begin(), currentBases.end(), &contract) != currentBases.end());
		}
		if (contract.isLibrary())
			for (FunctionDefinition const* function: contract.definedFunctions())
				if (function->isVisibleInDerivedContracts())
					members.push_back(MemberList::Member(
						function->name(),
						FunctionType(*function).asMemberFunction(true),
						function
					));
		if (isBase)
		{
			// We are accessing the type of a base contract, so add all public and protected
			// members. Note that this does not add inherited functions on purpose.
			for (Declaration const* decl: contract.inheritableMembers())
				members.push_back(MemberList::Member(decl->name(), decl->type(), decl));
		}
		else
		{
			for (auto const& stru: contract.definedStructs())
				members.push_back(MemberList::Member(stru->name(), stru->type(), stru));
			for (auto const& enu: contract.definedEnums())
				members.push_back(MemberList::Member(enu->name(), enu->type(), enu));
		}
	}
	else if (m_actualType->category() == Category::Enum)
	{
		EnumDefinition const& enumDef = dynamic_cast<EnumType const&>(*m_actualType).enumDefinition();
		auto enumType = make_shared<EnumType>(enumDef);
		for (ASTPointer<EnumValue> const& enumValue: enumDef.members())
			members.push_back(MemberList::Member(enumValue->name(), enumType));
	}
	return members;
}

ModifierType::ModifierType(const ModifierDefinition& _modifier)
{
	TypePointers params;
	params.reserve(_modifier.parameters().size());
	for (ASTPointer<VariableDeclaration> const& var: _modifier.parameters())
		params.push_back(var->annotation().type);
	swap(params, m_parameterTypes);
}

u256 ModifierType::storageSize() const
{
	solAssert(false, "Storage size of non-storable type type requested.");
}

string ModifierType::identifier() const
{
	return "t_modifier" + identifierList(m_parameterTypes);
}

bool ModifierType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	ModifierType const& other = dynamic_cast<ModifierType const&>(_other);

	if (m_parameterTypes.size() != other.m_parameterTypes.size())
		return false;
	auto typeCompare = [](TypePointer const& _a, TypePointer const& _b) -> bool { return *_a == *_b; };

	if (!equal(m_parameterTypes.cbegin(), m_parameterTypes.cend(),
			   other.m_parameterTypes.cbegin(), typeCompare))
		return false;
	return true;
}

string ModifierType::toString(bool _short) const
{
	string name = "modifier (";
	for (auto it = m_parameterTypes.begin(); it != m_parameterTypes.end(); ++it)
		name += (*it)->toString(_short) + (it + 1 == m_parameterTypes.end() ? "" : ",");
	return name + ")";
}

string ModuleType::identifier() const
{
	return "t_module_" + std::to_string(m_sourceUnit.id());
}

bool ModuleType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	return &m_sourceUnit == &dynamic_cast<ModuleType const&>(_other).m_sourceUnit;
}

MemberList::MemberMap ModuleType::nativeMembers(ContractDefinition const*) const
{
	MemberList::MemberMap symbols;
	for (auto const& symbolName: m_sourceUnit.annotation().exportedSymbols)
		for (Declaration const* symbol: symbolName.second)
			symbols.push_back(MemberList::Member(symbolName.first, symbol->type(), symbol));
	return symbols;
}

string ModuleType::toString(bool) const
{
	return string("module \"") + m_sourceUnit.annotation().path + string("\"");
}

string MagicType::identifier() const
{
	switch (m_kind)
	{
	case Kind::Block:
		return "t_magic_block";
	case Kind::Message:
		return "t_magic_message";
	case Kind::Transaction:
		return "t_magic_transaction";
	default:
		solAssert(false, "Unknown kind of magic");
	}
	return "";
}

bool MagicType::operator==(Type const& _other) const
{
	if (_other.category() != category())
		return false;
	MagicType const& other = dynamic_cast<MagicType const&>(_other);
	return other.m_kind == m_kind;
}

MemberList::MemberMap MagicType::nativeMembers(ContractDefinition const*) const
{
	switch (m_kind)
	{
	case Kind::Block:
		return MemberList::MemberMap({
			{"coinbase", make_shared<IntegerType>(0, IntegerType::Modifier::Address)},
			{"timestamp", make_shared<IntegerType>(128)},
			{"blockhash", make_shared<FunctionType>(strings{"uint"}, strings{"bytes32"}, FunctionType::Kind::BlockHash)},
			{"difficulty", make_shared<IntegerType>(128)},
			{"number", make_shared<IntegerType>(128)},
			{"gaslimit", make_shared<IntegerType>(128)}
		});
	case Kind::Message:
		return MemberList::MemberMap({
			{"sender", make_shared<IntegerType>(0, IntegerType::Modifier::Address)},
			{"gas", make_shared<IntegerType>(128)},
			{"value", make_shared<IntegerType>(128)},
			{"data", make_shared<ArrayType>(DataLocation::CallData)},
			{"sig", make_shared<FixedBytesType>(4)}
		});
	case Kind::Transaction:
		return MemberList::MemberMap({
			{"origin", make_shared<IntegerType>(0, IntegerType::Modifier::Address)},
			{"gasprice", make_shared<IntegerType>(128)}
		});
	default:
		solAssert(false, "Unknown kind of magic.");
	}
}

string MagicType::toString(bool) const
{
	switch (m_kind)
	{
	case Kind::Block:
		return "block";
	case Kind::Message:
		return "msg";
	case Kind::Transaction:
		return "tx";
	default:
		solAssert(false, "Unknown kind of magic.");
	}
}

